# 人生第二大脑 Android

一个原生 Android 日常前端。Google Drive 中的 Obsidian Vault 是唯一真实数据源；本地数据库只做加密缓存和离线队列。

当前版本 `0.1.0`（versionCode 1），包名 `com.lifeos.secondbrain`。

> **克隆后构建前必读**：`app/libs/libXray.aar`（94.5 MB）刻意不纳入版本控制，缺失时构建会失败。恢复方式见 `THIRD_PARTY_REFERENCES.md`。

## 已实现

- Kotlin + Jetpack Compose + Material 3 底层能力，四个主入口：现在 / 任务 / 灵感 / 档案。
- Google Credential Manager 身份层 + `AuthorizationClient` Drive 授权层。
- Drive API v3 REST：递归扫描、文件读取/更新/创建、文件夹创建、Changes API。
- Markdown / YAML Frontmatter 容错解析；分类依据 `type`，不依赖目录。
- Markdown 精准 patch：完成任务只修改 `status` / `updated`，尽量保留正文、未知 Properties、链接和原格式。
- Room 缓存 + SQLCipher 全库加密；数据库随机口令由 Android Keystore 保护。
- Room FTS4 搜索；额外生成中文单字/二元词索引，避免中文连续文本只能整句命中的问题。
- Full Scan + Drive Changes 增量同步；文件夹拓扑变化时回退为安全 Full Scan。
- 离线 PendingOperations + WorkManager 网络恢复自动重试。
- 离线完成任务：本地 UI 立即完成，联网后写回 Drive。
- 文字 / 语音 Capture；无网时原文立即进入加密本地缓存和待同步队列，联网后换成真实 Drive File ID。
- Capture 目录解析会优先复用现有 `00 Inbox/Quick Capture` / `00 Inbox/Voice Inbox` 或根目录已有同名文件夹，避免制造重复目录。
- OpenAI-Compatible `AiProvider`：Base URL / Model / API Key 配置、结构化 JSON 验证、AI 生成 Markdown、Raw Capture 保留不覆盖。
- API Key 使用 Android Keystore 加密，不写入 DataStore、Git 或日志。
- 任务分段：今天 / 近期 / 全部 / 完成；灵感分段：想法 / 计划 / 创作 / 学习。
- 档案页支持本地全文搜索、类型和日期过滤。
- 高级视觉基础层：浅/深模式、克制半透明玻璃材质、Spring 按压反馈、减少动态效果开关。
- 笔记详情页：四个页签的卡片均可点开，显示完整正文（常见 Markdown 结构会渲染为标题 / 复选框 / 列表 / 引用 / 代码块）、全部属性与标签，支持标记完成与在 Drive 中打开原文。详情页只读，不修改 Vault。
- 全局动效：页面转场、页签横向滑动、卡片级联入场、列表增删过渡，全部受"减少动态效果"设置控制。不依赖第三方动画库。
- 四个页签统一下拉刷新。
- 模板 / 系统文件夹过滤（忽略列表可配置，含向上遍历祖先目录判断，可拦截 `99 System/Prompts/...` 这类深层目录）。
- App 内独立代理：手动 SOCKS5 / HTTP（可选账号密码，密码经 Keystore 加密），只作用于本 App。
- 内置 Xray 内核直连自建 VPS（VLESS + Reality），不依赖系统 VPN、不依赖 FlClash；详见 `THIRD_PARTY_REFERENCES.md`。
- 自适应应用图标（前景 / 背景 / 传统 / 圆形，含各密度）。

## 数据原则

```text
Google Drive Markdown (truth)
        ↓
Encrypted Room cache + FTS index
        ↓
Kotlin Flow
        ↓
Jetpack Compose UI
```

Room 可以随时重建。任何真正需要永久保存的人生内容最终都必须对应 Drive 中的 Markdown。

## 隐私原则

- 不接广告 SDK，不默认接 Analytics，不发送 Vault 内容到行为分析服务。
- Raw Capture 不由 AI 覆盖。
- AI 只产生结构化结果；真正的 Drive 写入由 App 完成。
- Google Access Token、AI Key、完整人生正文不写普通日志。
- 本地正文缓存与 FTS 索引使用 SQLCipher 加密数据库保存。

## 当前构建状态

工程固定为 `compileSdk/targetSdk 37`、AGP `9.4.0`、Kotlin `2.2.10`、Gradle `9.6.0`。

**Debug APK 已真实产出并完成真机验证**（2026-09-21，Redmi Note 13 Pro / Android 13 / arm64）：Google 登录与 Drive 授权、Vault 发现、全量扫描、增量同步双向验证、Capture 写回 Drive、加密缓存 + 中文 FTS 搜索、下拉刷新、模板过滤、手动代理、内置内核直连均已实测通过。

交付定义（三条全绿且 APK 存在）：

```powershell
.\gradlew.bat testDebugUnitTest   # 19 个单元测试
.\gradlew.bat lintDebug           # 当前 0 error
.\gradlew.bat assembleDebug
```

构建环境与产物路径见交接文档 `Handoff/HANDOFF-2026-09-21.md`。

**当前 APK 为 debug 签名且 `debuggable=true`，不可对外分发。**

## 版本控制

本目录是独立 Git 仓库（`main` 分支）。`app/libs/libXray.aar` 刻意不入库，见上文；其余源码完整。

注意 `BuildOutput/`、`Handoff/`、`Akile_local_only.yaml`、`Tools/`、`Dev/`、`GithubOpenSource/` 均在本仓库之外，不属于版本控制范围。

## 文档

- `ARCHITECTURE.md`：数据、同步、离线、冲突和安全架构。
- `GOOGLE_SETUP.md`：Google Cloud / OAuth / Web Client ID / SHA-1 配置。
- `BUILD_OFFLINE.md`：历史记录——某个无外网沙盒要完成真实构建还缺什么（该阻塞已解除，保留作参考）。
- `THIRD_PARTY_REFERENCES.md`：libXray 内核的来源 / 版本 / 恢复方式，以及 Liquid Glass 等视觉参考项目。
- `KNOWN_LIMITATIONS.md`：仍未完成或尚未真机验证的事项。
