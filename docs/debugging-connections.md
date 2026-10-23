# 单设备调试连接 / One debugging channel per box

This vendor Android 5.1 target does not reliably handle competing host/local ADB connections. Multiple projects can start separate desktop ADB servers and produce confusing results: one server reports the box as `offline` while another has an authenticated `device` transport.

在本次排查中，5037 显示离线，5038 却已正常连接。单个服务的离线状态、甚至新 TCP 握手超时，不能直接证明盒子调试进程死锁。先核对所有正在运行的 ADB 服务，再复用已有的正常连接。

```sh
adb -P 5037 devices -l
adb -P 5038 devices -l
# Example only: use the port that already has the authorized device.
adb -P <ACTIVE_SERVER_PORT> -s <BOX_IP>:5555 shell getprop ro.product.model
```

- Do not start another server just to work around `offline`; inspect existing listeners first.
- Reuse one healthy transport and serialize device writes across projects.
- Disconnect stale offline transports explicitly. If a reconnect attempt stays offline, disconnect that stale transport before retrying the same server.
- Do not indiscriminately kill an ADB server used by another task.
- After installation/validation, disconnect the computer's target transport so the on-box bootstrap can acquire loopback ADB.

```sh
adb -P <ACTIVE_SERVER_PORT> disconnect <BOX_IP>:5555
```

The keeper repairs panel/app failures, not competing debugging projects on the computer. A blocked/revoked local ADB path can still delay server recovery. Daily iPad use should not keep a computer debugging transport attached.
