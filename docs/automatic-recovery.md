# 自动恢复 / Automatic recovery (UI3)

UI3 adds an optional independent companion, `local.m13.ipadkeeper`. Both APKs must use the **same signing certificate**.

## 中文

守护程序有独立的进程、开机接收器、前台服务和非唤醒定时检查。约每 15 秒检查一次面板的强停状态与本机 HTTP 健康状态，发现故障后通过受签名权限保护的无界面入口唤起面板。面板自己的开机接收器仍保留。更新面板时会关闭旧的独立 HTTP 进程，让新代码重新启动，长期登录密钥不变。

它不需要 root、不修改轻桌面或系统，也不持有家庭密码、ADB 私钥或任意命令执行接口。普通第三方应用不能调用恢复入口；只有相同签名并持有所需权限的守护程序可以。

### 安装

先安装 UI3 面板，再安装守护 APK，最后在电视打开一次「iPad 面板守护」。首次安装或恢复时电脑可能需要确认本机调试；日常守护在盒子上运行。

```sh
adb -s <BOX_IP>:5555 install -r M13-iPad-Remote-0.8-ui3.apk
adb -s <BOX_IP>:5555 install -r M13-iPad-Keeper-0.1.apk
adb -s <BOX_IP>:5555 shell am start -n local.m13.ipadkeeper/.MainActivity
adb disconnect <BOX_IP>:5555
```

原地址与密码不变。面板设置里的「停止桌面服务」会同时暂停守护，尊重主动停用；重新打开面板或选择启动会恢复守护。也可以单独在守护应用里暂停。卸载守护后，面板仍保留原有功能，但不再有外部恢复能力。

### 保证边界

这是自动恢复机制，不是系统级“禁止强停”。如果面板和守护都被强停/禁用、守护未获开机机会、调试授权被撤销、系统固件更新改变权限、设备断电/断网或应用数据被清除，仍可能需要人工处理。强停中的普通 Android 应用不能承诺自行启动，见 [Android 官方关于 stopped 状态的说明](https://developer.android.com/about/versions/15/behavior-changes-all#stopped-state)。实现针对 M13 / API 22；不要据此推断现代 Android 的后台启动行为。

实机验收记录见 `validation.md`。修改代码能构建、能主动开机与实际断电后自动恢复，是不同的验证项目。

## English

The independent keeper checks the panel's stopped flag and loopback HTTP health approximately every 15 seconds. It can launch an invisible, signature-protected recovery activity, clearing the panel's stopped state and starting its existing bridge. Boot receivers, a foreground service and a non-wakeup repeating alarm provide redundancy. The panel update receiver shuts down the old standalone HTTP process before restarting the new build; login secrets remain in private preferences.

Install the panel first, then the **same-signed** keeper APK, open the keeper once and disconnect computer ADB. Stopping the panel through its settings also pauses the keeper. The companion has no household password, ADB private key, root requirement or general command interface.

This does not make force-stop impossible. Stopping/disabling both packages, revoked debugging authorization, changed firmware policy, unavailable network/power or cleared app data can still prevent recovery. Tested scope is M13 / API 22; modern Android background-launch restrictions differ. See the official [stopped-state explanation](https://developer.android.com/about/versions/15/behavior-changes-all#stopped-state) and the separately recorded device tests.

### Build both APKs

```sh
# Same private keystore and local M13_KEYSTORE_PASSWORD for both commands.
python build.py --sdk /path/to/sdk --java-home /path/to/jdk --keystore .local/remote.jks
python build.py --sdk /path/to/sdk --java-home /path/to/jdk --keystore .local/remote.jks --keeper
```

To return to UI2, pause/uninstall the keeper first, then install a same-signed UI2 APK in place. UI3 intentionally retains the existing manifest versionCode for this local rollback path. The web label and health `recoveryVersion` distinguish UI3.
