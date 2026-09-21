# AGENTS.md — 给接手的 AI / 开发者

这份文档是给**下一个接手这个项目的 AI 或人**看的。它不讲功能，只讲：什么绝对不能做、代码怎么组织、现在到哪一步、以及哪些坑已经踩过。

先读这一份，再读 `README.md` 和 `ARCHITECTURE.md`。

---

## 1. 四条不可违背的原则

违反其中任何一条都会破坏这个应用的立身之本。改代码前请先确认没有触碰它们。

### 1.1 Google Drive 中的 Markdown 是唯一真实数据源

本地数据库（Room + SQLCipher）**只是可随时重建的缓存**。任何时候删掉本地库，都必须能从 Drive 完整重建。

推论：
- 不要把本地库当权威。不要写"先存本地、稍后再想同步"的逻辑。
- 不要在本地生成只存在于本地、永远不会写回 Drive 的"人生内容"。
- 新增任何本地表/字段时，问自己：这个数据如果丢了，能不能从 Drive 重建？如果不能，那它必须有一条写回 Drive 的路径。

### 1.2 AI 只返回结构化结果，Markdown 由 App 生成

AI 的输出永远不直接落盘。流程是：AI 返回结构化 JSON → App 校验 → App 生成 Markdown → App 写入。

推论：
- **不要让 AI 直接改写既有笔记正文。** AI 失败或返回垃圾时，原文必须完好无损。
- 结构化结果必须经过校验（见 `ai/AiProvider.kt` 与 `AiResultValidator`），不要信任模型返回的字段。

### 1.3 Raw Capture 原文优先

用户口述/输入的原文**先安全落盘**（本地加密库 + 待同步队列），之后才尝试 AI 整理。

推论：
- AI 整理失败**绝不能**导致原文丢失。当前实现保证这一点，改动时不要破坏。
- 不要把"AI 整理成功"作为保存原文的前置条件。

### 1.4 离线写入必须进队列

离线时的写入进 `PendingOperations`，联网后由 WorkManager 写回 Drive。

推论：
- **不要清空待同步队列。** 那里可能有尚未写回 Drive 的**唯一副本**。数据库迁移的约束见 5.1：正因如此，禁止使用 destructive migration。
- 队列里的补丁在重放时可能来自旧版本（payload 格式不同）。改动补丁格式时请保留对旧格式的读取，否则旧条目会永久卡在重试里。

---

## 1.5 任务可见性只有一处定义

`domain/TaskVisibility.kt` 是"今天该看到什么任务"的**唯一**判定来源。Home、Tasks、以后任何新页面都必须调用它，不要在页面里自己写 due/status 判断。历史上 Home 与 Tasks 各写了一份并已漂移过一次。

---

## 2. 代码结构

```
app/src/main/java/com/lifeos/secondbrain/
├── AppContainer.kt          # 依赖装配根
├── SecondBrainApp.kt        # 进程启动：设置观察 → 内核/代理生命周期
├── MainActivity.kt          # 登录授权入口 + Compose 宿主
├── ai/AiProvider.kt         # OpenAI 兼容 AI + 结果校验
├── data/markdown/           # Frontmatter 解析 / 精准 Patch / Note 映射
├── data/search/             # CJK 单字 + 二元词 FTS 分词
├── data/repository/         # 笔记仓储
├── database/                # Room + SQLCipher（entities / dao / mappers）
├── domain/                  # 领域模型（LifeNote 等）
├── drive/                   # Drive API v3 + 授权管理（含 401 自动重取）
├── identity/                # Credential Manager 身份层
├── network/                 # AppProxy（仅本 App 的代理）/ XrayTunnel（内置内核）
├── security/                # Keystore AES-GCM 密钥库
├── settings/                # DataStore 设置
├── sync/                    # SyncEngine / SyncWorker / SyncFilter / CapturePathPolicy
└── ui/                      # Compose 界面层
    ├── AppRoot.kt           # 外壳：页签 / 覆盖层 / 转场 / 状态指示器
    ├── Motion.kt            # 统一动效语汇（含 DepthMotion）
    ├── Glass.kt             # 玻璃材质 + 可点击卡片
    ├── TimeFormat.kt        # frontmatter 时间容错解析
    └── note/                # 详情页 + Markdown 渲染
```

关键约束：**项目必须放在纯 ASCII 路径下。** 中文路径会导致 AGP 拒绝构建。

---

## 3. 构建与验证

```powershell
# 在仓库根目录
.\gradlew.bat testDebugUnitTest   # 单元测试
.\gradlew.bat lintDebug           # Lint（当前 0 error）
.\gradlew.bat assembleDebug       # 产出 app\build\outputs\apk\debug\app-debug.apk
```

**交付定义：以上三条全绿且 APK 存在。**

### 3.1 动手前必须知道的一件事

`app/libs/libXray.aar`（94.5 MB）**不在仓库里**。缺它构建会失败。

从上游 XTLS/libXray 获取 `v26.9.9` 的 Android AAR 放回该路径。来源、版本、恢复方式与升级约束见 `THIRD_PARTY_REFERENCES.md`。**只读源码不需要这个文件。**

### 3.2 改 UI 的人请先读 `Motion.kt`

所有动效都必须受"减少动态效果"设置（`settings.reduceMotion`）控制并降级为 `snap()`。新增动画时请沿用 `Motion` 里的语汇，不要自己写裸 `tween`。

两个已踩过的坑：
- 先判 `reduceMotion` 再 `remember` / `LaunchedEffect`，否则会出现"设置了减少动效却仍在跑动画"。
- `slideInHorizontally` 在本项目 Compose 版本里，规格类型是 `FiniteAnimationSpec<IntOffset>` 而偏移 lambda 是 `Function1<Int, Int>`。**两者类型不同**，这是故意的。方向逻辑请放在 `DepthMotion` 里以便单测。

---

## 4. 现在到哪一步了

**已真机验证**（Redmi Note 13 Pro / Android 13 / arm64）：Google 登录与 Drive 授权、Vault 发现、全量扫描、增量同步双向验证、Capture 写回、加密缓存 + 中英文 FTS、下拉刷新、模板过滤、手动代理、内置 Xray 内核直连、笔记详情页、全局动效、四种返回路径。

**尚未完成**：发布签名 / R8、完整对抗式审查遗留项（见第 5 节）、关闭系统 VPN 后的独立性最终验证。

当前 APK 为 **debug 签名且 `debuggable=true`，不可对外分发**。

---

## 5. 已知问题（按优先级）

| 编号 | 问题 | 影响 |
|---|---|---|
| ~~H5~~ | ~~`fallbackToDestructiveMigration` 会连待同步队列一起清空~~ | **已修复**（见 5.1）。现为显式迁移，destructive fallback 已移除 |
| **H6** | 写回无 If-Match / ETag | 多端同时编辑会静默覆盖（丢更新）。**当前最高优先级** |
| M2 | 增量更新会把笔记 `path` 置空 | 缓存信息丢失 |
| M3 | Vault 发现是全盘搜名取第一个 | 同名文件夹时可能连错库 |
| M4 | 旋转屏幕触发重复同步 | 冗余同步 |
| M5 | AI 测试连接会向当前 Base URL 发送已存 Key | 误粘贴恶意地址可致 Key 泄露 |
| M6 | SQLCipher 口令读取失败会静默换新 | 极端情况下旧库永久打不开且无恢复入口 |
| M7 | 主线程做 Keystore / DB 初始化 | 启动卡顿（实测冷启动 `Skipped ~62 frames`） |
| M8 | 待同步队列无限重试、无死信 | 永久失败项耗电耗流量、用户无感知 |
| M9 | AI 整理非幂等、失败无重试入口 | 部分成功会留半套产物 |
| M10 | 消息单通道互相取消 | 连续提示可能丢失前一条 |
| L2/L3 | `tab` / `settingsOpen` / `openNote` 用 `remember` 而非 `rememberSaveable` | 旋转后丢失当前页签并弹回主页 |
| L4 | 档案页搜索无防抖 | 逐字符触发 FTS 查询 |

完整审查记录见 `Handoff/HANDOFF-2026-09-21.md`。

### 5.1 数据库迁移：**不要再加回 destructive fallback**

`AppDatabase.create()` 现在**没有** `fallbackToDestructiveMigration()`，这是刻意的，请不要"为了方便"加回来。

原因：`pending_operations` 可能保存尚未写回 Drive 的**唯一副本**，而 notes 表是离线视图。为了让一次 schema 变更顺利通过就清库，会毁掉这个应用本该保护的数据。

所以：**每次改 schema 都必须写显式 migration**，并同步提升 `version`。参考 `MIGRATION_3_4`（给 notes 加 `start` / `repeat` / `last_completed`）。

三个容易踩的点：

1. **列名必须三处一致**：实体属性名、`@ColumnInfo(name = ...)`、迁移 SQL。Kotlin 会把 `lastCompleted` 默认映射成驼峰列名，而 frontmatter 键是 `last_completed`；若不显式指定 `@ColumnInfo(name = "last_completed")`，Room 会在**打开数据库时**因 schema 校验失败而抛异常。这一点在开发期就被 schema 比对抓住了。
2. **迁移后必须核对导出的 schema**：`app/schemas/.../4.json` 由 KSP 生成，比对"上一版缺失的列"与迁移 SQL 是否吻合。这是编译期就能发现不一致的地方，别等运行时。
3. **FTS 表（`note_fts`）是 FTS4 虚拟表**，Room 不会为它要求迁移，不要手写。

### 5.2 周期任务（recurring tasks）

`repeat: daily` 的任务**永远不会变成 `status: done`**。每次完成写入 `last_completed`，第二天自动重新成为"今天"的任务。

```yaml
type: task
status: active
start: 2026-09-21
repeat: daily
last_completed: 2026-09-21
```

判定规则集中在 `domain/TaskVisibility.kt`，**Home 与 Tasks 都必须走它**。此前两个页面各自维护了一份 due 日期规则并且已经漂移过一次，加周期任务会把 bug 面积翻倍。

四个必须知道的约定：

- **不要每天新建 Markdown 文件。** 一个习惯 = 一个文件，靠日期动态出现在"今天"。
- **周期任务不进"完成"列表**（`isDoneTask` 明确排除）。否则每个习惯每天都会永久增加一条记录，几天就把列表刷满。
- **`due` 对周期任务不参与判定。** 一次性任务里 `due` 是截止日，但周期任务里它会变成"系列起始"，那不是用户写 `repeat: daily` 的本意。起始日用 `start`。
- **完成消息措辞**：周期任务不能说"已完成"（暗示进完成列表），要说"今天这项完成了，明天会再出现"。

完成计划由 `planTaskCompletion()` 产出，与写入动作分离，因此可以在没有数据库、没有 Drive、没有时钟的情况下单测。相关测试：`TaskVisibilityTest`（规则）与 `RecurringTaskRoundTripTest`（补丁确实能写进 Markdown 并被解析回来——"规则对但文件格式错"只有它能发现）。

---

## 5.3 UI 约定

**卡片的右侧箭头**：`GlassSurface` 的箭头是**固定 trailing slot**（10dp 间距 + 24dp 槽位），内容用 `weight(1f)` 约束到剩余宽度。不要改回"靠 end padding 留白 + 图标叠加"的写法——那样文本会按整张卡片宽度测量，长行会钻到箭头下面。改完请实测：文本右界必须小于箭头左缘。

**工具栏状态条**：同步/保存状态放在 `TopAppBar` 的 `navigationIcon` 槽位，因此与右上角设置按钮同高（实测两者中心 y 相同），且工具栏高度恒定，出现/消失不会推动下方内容。不要挪回内容区。

---

## 6. 安全红线

**禁止提交到仓库**：VPS 节点凭据（UUID / PublicKey / ShortId / SNI）、订阅链接 token、Google Web Client ID、AI API Key、SQLCipher 口令、VPS SSH 凭据。

当前状态：仓库内**不含任何真实凭据**（`GOOGLE_SETUP.md` 里的是占位符）。真实的 `GOOGLE_WEB_CLIENT_ID` 由 `providers.gradleProperty` 从**仓库外**的用户级 `gradle.properties` 读取。

其他：
- API Key 与代理密码经 Android Keystore 加密存于私有 SharedPreferences，**不落盘明文、不进日志**。
- 节点信息存于 App 私有 DataStore（root 可读，普通应用不可读）。
- 发布前需过一遍合并清单：当前含 `USE_BIOMETRIC` / `USE_FINGERPRINT`（credentials 库带入）与 debug 专用导出组件。

---

## 7. 许可证状态（重要）

**本仓库当前没有许可证（No License）。** 即默认「保留所有权利」：你可以阅读、学习、提 issue，但**没有明示授权去复制、修改或再分发代码**。

如需复用，请先联系仓库所有者。

另外：本应用**打包了 MIT 许可的 libXray 内核**。MIT 要求分发二进制时保留版权与许可声明。因此若将来对外发布 APK，需在分发包中附上 libXray 的许可证声明（该文件本身不入库）。`THIRD_PARTY_REFERENCES.md` 记录了完整的第三方清单与各自许可证。
