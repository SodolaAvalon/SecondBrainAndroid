# Offline / sandbox build notes

## 当前沙盒已经具备

- JDK 21（Android 项目生成 Java 17 bytecode）。
- Gradle 9.6.0：`/mnt/data/gradle-9.6.0`。
- 完整 Gradle Wrapper：`gradlew`、`gradlew.bat`、`gradle-wrapper.jar`、`gradle-wrapper.properties`。
- Android command-line tools：`/mnt/data/android-sdk/cmdline-tools/latest`。
- Android SDK Platform 37 revision 2：`/mnt/data/android-sdk/platforms/android-37`。
- Android Build Tools 36.0.0：`/mnt/data/android-sdk/build-tools/36.0.0`。
- Android Platform Tools 37.0.1：`/mnt/data/android-sdk/platform-tools`。

SDK 包的 `source.properties` 和工具可执行文件已经在沙盒中验证，`android.jar`、`aapt2`、`adb` 均可用。

## 当前唯一的外部构建阻塞

沙盒终端无法解析外网域名，因此 Gradle 无法连接：

- Google Maven
- Maven Central
- Gradle Plugin Portal

第一次真实 Gradle 配置已经执行，最先失败在：

```text
Plugin [id: 'com.android.application', version: '9.4.0'] was not found
```

这不是项目声明错误，而是 AGP 9.4.0 尚未进入本地 Gradle cache；Gradle 无法从 Google Maven 下载它。AGP 到位以后，Compose、Room、AndroidX、OkHttp、SQLCipher 等依赖也需要对应缓存。

## 需要的离线依赖缓存

最稳妥的方式是在一台能访问 Google Maven、Maven Central 和 Gradle Plugin Portal 的机器上，用本项目执行一次：

```bash
./gradlew --refresh-dependencies :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

然后把该次构建使用的 `GRADLE_USER_HOME` 整体打包带入沙盒。为了避免污染用户已有缓存，可以在联网机器上先指定一个独立目录：

### Linux / macOS

```bash
export GRADLE_USER_HOME="$PWD/offline-gradle-home"
./gradlew --refresh-dependencies :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

### Windows PowerShell

```powershell
$env:GRADLE_USER_HOME = "$PWD\offline-gradle-home"
.\gradlew.bat --refresh-dependencies :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

构建完成后只需要把 `offline-gradle-home` 压缩上传；沙盒即可用同一缓存执行离线构建。

## 完成标准

在真正宣布 APK 完成之前，必须实际成功执行：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

并确认 `app/build/outputs/apk/debug/` 下存在可安装 APK。当前 SDK 工具链已经就绪，但 Maven/Gradle 依赖缓存尚未到位，因此此里程碑仍未生成 APK。
