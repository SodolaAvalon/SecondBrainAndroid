# Google Drive / OAuth 设置

V1 仅供个人使用。为了访问并修改现有完整 Obsidian Vault，本版本请求：

```text
https://www.googleapis.com/auth/drive
```

如果以后公开发布，必须重新评估最小权限策略（优先考虑 `drive.file + Google Picker`），并按 Google 当时的验证要求处理受限 scope。

## 1. Google Cloud

1. 创建 Google Cloud / Google Auth Platform 项目。
2. 启用 **Google Drive API**。
3. 配置 OAuth consent screen。
4. 在测试阶段，把自己的 Google 账号加入测试用户。

## 2. Android OAuth Client

创建 Android OAuth Client：

- Package name：`com.lifeos.secondbrain`
- SHA-1：对应实际签名证书。

Debug 阶段通常使用 debug keystore 的 SHA-1；Release 使用正式签名证书的 SHA-1。

当前本机 Debug 签名指纹（2026-09-20 由 APK 实测验证；签名 keystore 为 `ANDROID_USER_HOME=D:\Tools\Android\.android\debug.keystore`）：

```text
SHA-1:   6E:FF:A4:7E:90:22:D9:46:56:46:D8:7A:2D:2B:DD:01:BC:31:87:6D
SHA-256: A3:E4:70:A1:AC:58:1F:71:3E:4A:0D:5D:A3:C8:5D:97:71:38:7B:B3:92:92:50:5D:25:47:38:DA:1F:C7:EC:6B
```

Android OAuth Client **没有需要塞进 APK 的 Client Secret**。不要把所谓 Client Secret、服务账号私钥或其他服务器凭据提交进项目。

## 3. Web OAuth Client ID（Credential Manager）

Credential Manager 的 Google ID 流程还需要一个 **Web application OAuth Client ID / server client ID**，它不是 secret，可以作为构建配置写入 App。

推荐放在本机 `~/.gradle/gradle.properties` 或项目未提交的 Gradle properties 中：

```properties
GOOGLE_WEB_CLIENT_ID=1234567890-xxxxxxxxxxxxxxxx.apps.googleusercontent.com
```

项目会把它编译成 `BuildConfig.GOOGLE_WEB_CLIENT_ID`。不要把真实账号凭据、access token 或 secret 放进仓库。

## 4. 两层授权

App 把两件事分开：

1. **Credential Manager**：用户选择 Google 身份。
2. **AuthorizationClient**：请求 Drive scope，拿到当前 Drive API 可用的授权结果。

这样不会把“登录”和“访问 Drive 数据权限”混成同一个旧式 Google Sign-In 流程。

## 5. 首次真机验证清单

- Android 设备已登录目标 Google 账号。
- App 的 package name 与 Google Cloud Android Client 完全一致。
- 当前 APK 签名证书 SHA-1 已加入 Android OAuth Client。
- Web Client ID 已通过 `GOOGLE_WEB_CLIENT_ID` 配置。
- Drive API 已启用。
- 测试账号已被允许使用 consent screen。
- 首次授权后能找到 / 指定 `Obsidion` Vault。
