# Architecture

## 1. Source of truth

```text
Google Drive -> Obsidian-compatible Markdown -> encrypted local cache -> Flow -> Compose UI
```

Google Drive 是唯一真实数据源。Room 只是可删除、可重建的本地缓存；离线产生的写操作进入 PendingOperations，最终仍必须落到 Drive Markdown。

## 2. Packages

- `ui`: Compose 页面、主题、玻璃材质、交互状态。
- `domain`: Android / Drive 无关的领域模型。
- `data/markdown`: Frontmatter 解析、Markdown patch、NoteMapper。
- `data/search`: 本地搜索 token 生成和 FTS 查询生成。
- `data/repository`: Room -> Domain Flow。
- `drive`: Drive API v3、AuthorizationClient。
- `identity`: Credential Manager Google 身份层。
- `sync`: Full Scan、Changes API、PendingOperations、WorkManager。
- `database`: SQLCipher Room cache、FTS4、同步元数据。
- `ai`: 可替换 `AiProvider` 与 OpenAI-Compatible 实现。
- `security`: Android Keystore 保护的 Secret Store。
- `settings`: Vault 和非敏感偏好。

## 3. Markdown identity and classification

- Drive File ID 是同步层稳定 ID。
- App 新建内容可以额外写 `id: UUID`。
- 类型只看 Frontmatter，例如 `type: task`；目录不决定类型。
- 损坏 / 非标准 Markdown 单文件解析失败不能拖垮整个 Vault 索引。

## 4. First sync

1. 用户完成 Google 身份选择与 Drive scope 授权。
2. 指定 / 自动发现 `Obsidion` Vault。
3. 递归枚举 Vault。
4. 只下载 Markdown，逐篇容错解析。
5. 写入加密 Room + FTS index。
6. 保存 Drive `startPageToken`。
7. UI 全程从 Room Flow 读取。

## 5. Incremental sync

启动时先显示本地 Room 数据，再后台：

1. 处理 PendingOperations。
2. 使用 Drive Changes API 获取变化。
3. Markdown 变化时验证文件仍在当前 Vault 的祖先链中。
4. 删除 / 移出 Vault 的文件从本地索引删除。
5. 文件夹本身发生拓扑变化时执行 Full Scan，因为 Drive 不保证为所有后代产生移动 change。

## 6. Offline writes

### Task complete

本地先将任务标记为 `done`，UI 立即响应；远程写失败时保存 `PATCH_TASK_DONE`，WorkManager 等网络恢复后重试。

### Capture

1. 先生成完整 Raw Markdown。
2. 在线则写 Drive 并缓存真实 Drive File ID。
3. 离线则写入加密 Room，使用 `pending:<uuid>` 临时 ID，并记录 `CREATE_CAPTURE`。
4. WorkManager 上传成功后删除临时记录，用真实 Drive File ID 替换。

Raw 原文不会因 AI 或网络错误丢失。

## 7. Capture path policy

优先级：

1. 已有 `00 Inbox/<Quick Capture|Voice Inbox>`。
2. Vault 根目录已有 `<Quick Capture|Voice Inbox>`。
3. 创建 / 复用 `00 Inbox`，再创建目标子目录。

目录只决定新文件写入位置，不参与内容类型判断。

## 8. Conflict rule

写已有文件前重新下载远程最新正文，在最新版本上只 patch 必要 Frontmatter 字段。例如完成任务只改：

```yaml
status: done
updated: ...
```

Patcher 不重新序列化整篇 Markdown，尽量保留正文、空行、字段顺序、Obsidian Links 与未知 Properties。

## 9. Search

Room 使用 FTS4 + `unicode61`。为了中文查询不被“整段汉字是一个 token”限制，入库前还会生成中文单字和重叠二元词；英文 / 数字按词并使用前缀匹配。

FTS 数据与正文一起保存在 SQLCipher 数据库中。

## 10. Local encryption

- SQLCipher 加密整个 Room 文件，包括正文和 FTS index。
- 每次安装首次运行生成随机数据库 passphrase。
- passphrase 本身由 Android Keystore AES-GCM key 加密后保存。
- AI API Key 使用同一个 Keystore-backed secret store，但与数据库 passphrase 使用不同逻辑 key。

## 11. AI boundary

`AiProvider` 只接收 Raw 文本并返回结构化对象。App 验证 JSON 后自行创建 Markdown；AI 没有任意 Drive 修改能力。

生成条目保留：

```yaml
source: drive:<raw-file-id>
```

Raw 本身只会被 patch `processed` / `processed_at`，不会覆盖原文。

## 12. UI / performance

- 所有主列表读取 Room Flow，并使用 LazyColumn。
- 普通列表项不做昂贵实时 blur。
- 默认 Glass 层是低成本半透明、细亮边、方向性高光和柔和阴影。
- 真折射 / backdrop blur 作为后续可选增强层，不能成为低端设备的性能前提。
- Reduce Motion 时按压和切换动画收敛或取消。
