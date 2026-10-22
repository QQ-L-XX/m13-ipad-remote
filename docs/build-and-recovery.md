# 构建与恢复 / Build and recovery

## Prerequisites / 环境

- Python 3.9+ (standard library only).
- JDK with `java`, `javac`, `keytool`; tested with JDK 21.
- Android SDK platform `android-22`.
- SDK Build Tools with `aapt`, `zipalign`, `lib/d8.jar`, `lib/apksigner.jar`; tested with 35.0.0.

`ANDROID_SDK_ROOT` / `ANDROID_HOME` and `JAVA_HOME` may be set instead of CLI arguments. Build outputs stay in ignored `.build/` and `dist/` directories. The build never installs anything on a box.

```sh
python build.py --sdk /path/to/sdk --java-home /path/to/jdk --unsigned
python scripts/test_auth.py --java-home /path/to/jdk
```

To create a signed APK, set `M13_KEYSTORE_PASSWORD` in your shell and run:

```sh
python build.py --sdk /path/to/sdk --java-home /path/to/jdk --keystore .local/remote.jks
```

Use `--key-alias` if needed (default: `remote`). If the keystore does not exist, the script creates it. Secrets are passed to Java tools via environment-variable references rather than literal command-line passwords. Keep the key and password privately backed up. A locally rebuilt APK with another key cannot overwrite the prebuilt signed release without first uninstalling it, which loses app data.

## Restore / 恢复

保留已验证 APK 和自己的签名密钥。优先使用相同签名 `adb install -r` 覆盖安装，保留应用数据中的密码、随机登录密钥和本机调试授权。安装后启动应用并断开电脑 ADB，再等待服务启动。具体盒子的私人恢复包只留在本地，不在本仓库。

Keep a known-good APK and your signing key. Prefer an in-place `adb install -r` update with the same signature to preserve the password, random login secret and local debugging key. Start the app, disconnect computer ADB and wait for on-box startup. Device-specific private recovery bundles are intentionally not published.

卸载或恢复出厂会清除私有设置，需要重新登录和本机调试授权。复制 APK 不能备份全部系统数据或第三方账号。播放器版本恢复须单独核对签名与设备管理员状态，不要用旧版覆盖更新版本。

Uninstalling or factory-resetting clears private settings. An APK copy is not a full-system/account backup. Player restoration requires separate version, signature and device-owner checks.

## Known setup detail / 初始化文案

The on-TV setup activity retains some early-version labels. The web header `0.8 · UI2` and `/health` authentication version identify the current web release. This does not grant universal video/audio support to other apps.
