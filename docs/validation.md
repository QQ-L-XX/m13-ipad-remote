# 验证范围 / Validation scope

日期 / Date: 2026-10-22. These are historical development records, not a universal compatibility guarantee.

| 检查 / Check | 结果与范围 / Result and scope |
|---|---|
| Real iPad desktop/input | User confirmed on A1432 / iOS 9.3.5 and M13 |
| Original movie picture/audio | User confirmed with the compatible custom player in 0.6 |
| Automatic movie transition | User confirmed in 0.7 |
| Play/pause/seek and closer progress | User confirmed in 0.8 with 2.22-sync1; not frame-exact |
| 720p30 video mirror | User reported smoother movie picture; no system audio |
| 60 fps | Not achieved |
| UI1 layouts | Six desktop-browser viewport sizes; fixed buttons visible and no image overlap |
| Authentication policy | 13 Java checks: stable tokens, password/secret changes, parsing and expiry |
| UI2 app/server restart | Ordinary app and shell daemon both stopped, HTTP confirmed down, then restarted; old cookie accepted without re-login |
| UI2 desktop browser restart | Persistent profile actually closed and reopened; direct desktop without entering password |
| UI2 real iPad login acceptance | Pending |
| UI2 full-box power cycle | Not repeated; current persistence test is app/server restart |
| Factory-reset recovery | Not tested; app-level backups are not full-system images |
| Other Android models/versions | Not tested |
| Launcher 2.24 playback synchronization | Not revalidated in this round |

真实 iPad 验收、后端检查、桌面浏览器检查分别记录。源码可构建或媒体可下载，不等于在所有旧 Safari 上已成功播放。电影效果取决于来源编码、本机播放器接口和 Wi-Fi。

Real-iPad acceptance, backend checks and desktop-browser checks are separate evidence. A successful build or downloadable stream is not proof of playback on every legacy Safari. Media results depend on source codecs, the native-player integration and Wi-Fi.

## UI3 recovery checks (2026-10-23)

- Panel force-stop plus standalone HTTP daemon loss: recovered automatically in 10.23 seconds, with the old cookie.
- Actual package replacement: recovered in 1.69 seconds with a different HTTP runtime ID, without manual app launch.
- Recovery activity rejects the shell caller; the same-signed companion is permitted.
- Physical full-box power cycle: verified, original cookie retained and no PC ADB transport used.
- Both packages force-stopped/disabled together and revoked debugging permissions remain outside the guarantee.
- Cold-boot verification used a changed kernel boot ID and a previously saved desktop-browser profile, reopened without entering a password. The first post-boot HTTP observation was at 37 seconds of device uptime; the outage duration was not measured.
- Real legacy-iPad UI3 login acceptance remains pending; earlier user-confirmed desktop/movie results are recorded separately above.
