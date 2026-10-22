# 播放器集成 / Player integration

## 中文

独立面板 APK 可以提供桌面显示与输入；自动影片原声和同步另依赖定制轻桌面 `local.m13.home`，并不兼容所有叫 LibreTV 的应用。

- `local.m13.home/.LibreTvProvision` 的受 DUMP 权限保护的有序广播 `local.m13.home.LIBRE_STATUS`，返回当前影片状态。
- 状态包含标题、来源、时长、进度、播放状态和本机播放器代理信息。`FilmRelay.java` / `AutoPlayback.java` 是实际协议实现的依据。
- 控制动作 `local.m13.home.LIBRE_CONTROL` 接受 `play`、`pause`、`seek`，带当前影片 key，拒绝旧影片标识；接收器权限不应放宽。
- HLS 回传只映射已经识别的本机回环代理资源，不提供任意 URL 转发，也不转码原片。

`native-sync/` 记录的是针对已确认 **2.22 / versionCode 30** 安装包的源码补丁。它依赖已知类名、字段与寄存器布局，**不是通用补丁程序**。需核对具体 APK、签名和完整备份，再由包的合法维护者构建。仓库没有提供完整播放器 APK、它的私钥或第三方素材。

相关定制轻桌面项目：[m13-offline-voice-usb-mic](https://github.com/QQ-L-XX/m13-offline-voice-usb-mic)。该仓库的版本也可能不同，不代表可以直接替换当前盒子安装包。

目前已验收的原声和同步结果来自 2.22-sync1 配套版本。后来现场安装的 2.24 没有在本轮重新验收播放同步。其他软件的系统声音没有实现；哔哩哔哩原界面的受保护画面路径未跑通，本仓库未加入绕过实现。

## English

The standalone APK handles desktop display and input. Original-audio handoff and synchronized playback require a **specific custom** `local.m13.home` player, not an arbitrary LibreTV installation.

The DUMP-protected `LibreTvProvision` receiver exposes ordered `LIBRE_STATUS` and `LIBRE_CONTROL` broadcasts. Control is limited to the current movie key and play/pause/seek. Consult `FilmRelay.java` and `AutoPlayback.java` for the implemented status protocol. Only identified loopback proxy resources are relayed; the server is not an arbitrary URL proxy or transcoder.

`native-sync/` is a source-only patch for a verified **2.22 / versionCode 30** binary with known fields and DEX layout. It is not a generic patcher. Version, signatures and backups must be verified by the package's authorized maintainer. Complete player APKs, private signing material and third-party assets are not included.

Historical real-device audio and synchronization acceptance used 2.22-sync1. Later 2.24 compatibility was not re-tested here. Other-app system audio and original-interface Bilibili streaming are not implemented.
