# 当前里程碑限制

## 构建状态（2026-09-21 更新）

早期某个无外网沙盒无法访问 Google Maven / Maven Central，导致构建受阻；该阻塞已解除。当前 `JAVA_HOME` / Android SDK / Gradle 缓存齐备，以下均已在真机验证的构建中达成：

- Gradle Android 编译通过；
- Android Lint 通过（0 error）；
- 19 个单元测试通过；
- Debug APK 已生成（`DBAPP/BuildOutput/SecondBrainAndroid-debug-0.1.0.apk`，73.2 MB，arm64-v8a）；
- 真机启动无崩溃。

历史背景见 `BUILD_OFFLINE.md`。构建环境与完整验证记录见 `Handoff/HANDOFF-2026-09-21.md`。

**已知限制**：APK 为 debug 签名且 `debuggable=true`，未加 R8，不可对外分发。

## 已真机验证

- Google OAuth Android Client / SHA-1 / Web Client ID 配置（实测登录 + 授权通过）。
- Credential Manager + AuthorizationClient 的正常授权与 Drive scope 流程。
- SQLCipher Room 初始化、加密缓存与中英文 FTS 搜索。
- WorkManager 网络恢复重试、离线 Capture 排队后写回 Drive。
- 增量同步双向验证、清缓存后从 Drive 完整重建。
- 手动代理与内置 Xray 内核直连两条网络路径的读写。

## 仍需验证 / 未完成

- **独立性最终验证**：现有内置内核测试均在 FlClash 系统 VPN 开启状态下完成，尚需关闭 FlClash VPN 后复测，才能确认完全不依赖系统 VPN。
- Credential Manager 的**取消授权、token 失效与重连**异常路径。
- SpeechRecognizer 语音 Capture 的真机麦克风与语音服务验证。
- 数据库**迁移策略**：当前 `fallbackToDestructiveMigration` 会连待同步队列一起清空，版本升级 + 改 schema 时可能丢失未写回 Drive 的唯一副本。
- 液态玻璃当前使用轻量材质实现；真正的实时 backdrop 折射还没有作为默认能力接入。
- 生物识别 App Lock 尚未实现。
- AI 自动整理的网络失败目前保证 Raw 原文安全，但还需要增加独立“稍后重新整理”的长期任务队列 / 手动重试入口，才能把 AI 重试体验做完整。

## Frontmatter 范围

V1 parser 重点支持 Obsidian 常见标量、inline list 和缩进 `- item` 列表。复杂嵌套 YAML、anchors、multiline block scalars 等不会被完整解释；损坏文件会按普通正文容错处理，不应导致整个索引崩溃。
