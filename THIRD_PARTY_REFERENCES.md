# Third-party components and references

本文档分两类内容：

- **运行时依赖**：真正打进 APK 的第三方二进制，目前只有 libXray。
- **视觉参考**：只用于研究、没有接入运行时依赖的仓库。

## libXray（XTLS/libXray）— 运行时依赖

内置的 Xray 内核。App 通过它在进程内直接建立 VLESS + Reality 隧道，不依赖系统 VPN，也不需要 FlClash。用于 `data/network/XrayTunnel.kt` / `TunnelController.kt`。

### 文件与版本

| 项 | 值 |
|---|---|
| 仓库 | https://github.com/XTLS/libXray |
| 授权 | MIT |
| 本仓库使用的版本 | `v26.9.9`（CalVer，即 2026-09-09） |
| 同 commit 的 Go 模块标签 | `v1.260909.0`（CalVer ≥ 2 的大版本号不能进 Go import path，故用 SemVer 镜像标签） |
| 产物 | `app/libs/libXray.aar`，94.5 MB |
| AndroidManifest package | `go.libXray.gojni`，`minSdkVersion 21` |

libXray 采用 CalVer `v<YY>.<M>.<D>`，因此 `v26.9.9` 对应 2026-09-09。构建脚本默认把 Xray-core 钉在 release tag `v26.9.9`。

### 为什么这个 AAR 不进版本控制

`app/libs/libXray.aar` 已被 `.gitignore` 排除。原因：

1. 它有 **94.5 MB**，接近 GitHub 单文件 100 MB 硬上限；走 Git LFS 则会快速消耗免费额度（1 GB 存储 / 1 GB 月流量），clone 几次就超。
2. 它是**上游预编译产物，不是本项目源码**，可以从上游按版本号完整复现。

代价：clone 下来后必须手动补回这个文件，否则构建失败（见下）。

### 如何恢复

AAR 内的 `jni/` 含四个 ABI 的 `libgojni.so`（armeabi-v7a 63.7 MB、arm64-v8a 48.5 MB、x86 64.0 MB、x86_64 51.5 MB）。当前 App 只打包 arm64-v8a。

从上游获取 `v26.9.9` 的 Android AAR，放到 `app/libs/libXray.aar`。上游按 `python3 build/main.py android` 用 gomobile 产出 AAR；如果重新编译，请改用同一个 Xray-core 版本，否则行为可能与真机验证过的版本不一致。

构建脚本引用处：`app/build.gradle.kts` 的 `implementation(files("libs/libXray.aar"))`。

### 使用方式与约束

App 只经由一个结构化入口调用内核：`LibXray.invoke(requestJSON)`，请求为 `{"apiVersion": 3, "method": ..., "payload": {...}}`。当前使用的 method 为 `runXray` / `stopXray` / `getXrayState` / `getFreePorts`。

**升级内核时必须注意**：libXray 不保证 API 稳定性，只与最新版 Xray-core 兼容。`apiVersion` 固定为 3，接口变更会要求消费端同步改造。

**一个进程只能有一个 Go runtime**：libXray 的每个原生产物都内嵌 Go runtime。不要在同一进程里再加载另一个独立构建的 Go / cgo / gomobile 库，否则可能在构建、链接、加载阶段失败，或在 `main` 运行前崩溃。如果确实需要多个 Go 库，必须用同一次 `gomobile bind` 一起产出。

## 视觉参考（未接入运行时依赖）

以下仓库由用户放入工作区用于研究视觉 / 渲染方案。当前 App 源码没有直接复制它们的实现代码，也没有把它们作为运行时依赖打进项目。

### AndroidLiquidGlass / Backdrop (Kyant0)

- 用途：研究 Compose backdrop、blur、lens、vibrancy 的组织方式和可组合 API。
- 工作区文件：`AndroidLiquidGlass-kmp.zip`
- 仓库内标注 Apache-2.0。
- 当前决策：暂不直接引入其预编译依赖，避免在尚未完成 Android 构建验证前引入 Kotlin / Compose 编译版本耦合。

### BuildItCode / LiquidGlass

- 用途：研究 API 33+ AGSL 与旧 Android 软件 fallback 的分层方式。
- 工作区文件：`LiquidGlass-master.zip`
- README 标注 MIT。
- 该实现包含 NDK / CMake 路径，第一版不把它作为默认渲染前提。

### Haze

- 用途：作为 Compose 背景模糊与材质效果的备选参考。
- 工作区文件：`haze-main.zip`
- 当前未接入运行时依赖。

### Compose Cupertino

- 用途：参考克制留白、控件比例与 iOS/Cupertino 交互节奏。
- 工作区文件：`compose-cupertino-master.zip`
- 当前不直接复制 iOS 视觉或 Apple 专有图标资源。

## 当前视觉实现原则

第一版默认 Glass Surface 使用低成本的：

- 半透明填充；
- 极细方向性亮边；
- 柔和阴影；
- 极弱高光；
- Spring 触感。

普通列表不使用每卡实时模糊。真正的折射 / backdrop blur 只会在后续构建与真机性能验证通过后，用于导航、Focus Card、Capture 等少量关键层级。
