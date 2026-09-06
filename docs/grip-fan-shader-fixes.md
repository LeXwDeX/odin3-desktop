# 2026-09-06 用户复测：握持横屏、自动停扇与盖世游戏

本轮从 `50eac77` 继续处理用户实际反馈。配置和应用数据保留，硬件和方向仍在 APK 内直接调用原厂服务，不启动旧电脑桥。代码发现采用 Tier 2，逐文件核对图谱覆盖，并直接核验生产源码；当前记录不能证明所有固件或游戏都无缺陷。

## 自动风扇

此前 >60°C 连续 16 秒的过滤仍有一个绕过分支：任何一次 >=75°C 的 CPU/GPU 热点读数立即进入智能。桌面切换和程序启动可产生短促热点；每 8 秒采一次也容易遗漏两次操作之间的降温。L1/R1 本身只切换标签，并不调用风扇操作。

现在 >=75°C 必须连续 4 秒确认，>=90°C 保留即时响应；>60°C 连续 16 秒和 <=55°C 连续 24 秒的恢复回差保留。温升确认和恢复期间每 2 秒采样，空闲每 8 秒采样。一个消费者处理广播和采样超时，温升后立即缩短下一次等待，不留下旧的 8 秒定时器。手动选档仍关闭自动调度；游戏、较高性能档和传感器失败的冷却处理不变。Debug 日志仅记录拟改变风扇时的温度和原因，Release 不输出这些诊断。

真机重新唤醒后，先重放低频肩键，再在新版本上连续重放 50 次 L1/R1；配置 `auto_fan_control_enabled=true`，模式、驱动 state 和 duty 始终为 0。按键注入本身也会带来 CPU 活动，这不是耗电基准或所有温度情况的证明。持续高温与恢复边界由真实生产 `ThermalGate` 的 JVM 回归验证，没有通过堵风口或关闭原厂热保护制造高温。

## 握持横屏，允许竖屏应用

实机旋转历史显示，盖世游戏 `PcEnginePluginHostActivity` 请求 `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`，即使 `accelerometer_rotation=0`、`user_rotation=1`，也会随传感器在 90°/270° 间翻转。普通旋转锁无法约束这个请求。旧实现还尝试启用 `force_landscape` 原厂顶层浮层，会覆盖竖屏请求，并吞掉写入异常。

改为固定的 `ORIENTATION` 原厂事务：

- 握持模式关闭 OEM `force_landscape` 浮层与强制忽略应用方向，设置 `persist.demo.remoterotation=landscape`、`persist.demo.rotationlock=true`，保留应用的横／竖屏类别请求。
- 按原有 `wm size` 值刷新旋转策略；有自定义分辨率时保留该值，无自定义值时使用 reset。不改成中间分辨率，不重启系统。
- 传感器模式解除该偏好锁并开启自动旋转。设置、属性和固定旋转模式读回成功后才保存应用偏好、显示选中；失败逐项恢复原值，包括缺失设置和空属性。
- Activity 自身方向设置只影响本窗口，系统事务在 IO 线程执行；不会在 Compose 重组时重复写系统。

依据 Android 15 [DisplayRotation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/services/core/java/com/android/server/wm/DisplayRotation.java) 与 [DisplayContent](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/services/core/java/com/android/server/wm/DisplayContent.java) 的方向决策及配置刷新路径。该内部接口在本机固件通过实测，固件升级后仍需复验。

真实应用 UID 的 `verify_orientation` 依次执行模式 1、0，再恢复原偏好 0，读回全部成功。隔离窗口方向矩阵：

| 应用请求 | 实际旋转 |
| --- | --- |
| LANDSCAPE / SENSOR_LANDSCAPE / REVERSE_LANDSCAPE | 90°，正常握持方向 |
| PORTRAIT / SENSOR_PORTRAIT | 0°，竖屏 |
| REVERSE_PORTRAIT | 180°，应用请求的反向竖屏 |

## 盖世游戏提示找不到游戏

设备上的应用监控服务未运行，而使用情况访问已经授予。旧磁贴只读引擎缓存，最后的缓存是桌面，因此无法取得游戏目标。新增 `ForegroundAppResolver` 在用户操作入口时读取已有权限下的 `ACTIVITY_RESUMED` 事件；后续只增量读取最近事件。仅跳过系统面板和本应用的校准覆盖窗口，返回桌面、切换应用、锁屏和息屏会丢弃旧游戏身份。不自动授予权限，也不读取其他应用的界面文字。

兼容 CRT 遮罩存在时才持续检查目标；遮罩移除后停止该检查。不增加常驻识别服务。校准面板支持同一回退；快速异步查询用版本检查避免旧结果覆盖新前台。

在盖世游戏前台不传 `package_name` 打开面板，实机文字已为“目标：盖世游戏”。最终版本还在盖世游戏真实前台通过系统快捷磁贴切换 `isEnabled: 1 → 0 → 1`，数据库整条配置与备份逐字段一致，未再提示找不到游戏。备份和读回位于忽略目录的 `gamehub-tile-before`、`gamehub-tile-off`、`gamehub-tile-restored`。

使用隔离 fixture、保持监控关闭的运行探针通过：开关关闭、CRT 遮罩绘制、系统面板返回、仅预览组合、无效果组合、浮层权限缺失／恢复、前台丢失／恢复以及返回桌面清除目标。探针恢复测试包配置和原浮层权限。

本修复解决目标识别，不把未接入游戏帧输入的 FXAA 等组合宣称为实时生效。CRT 遮罩仍显示“游戏效果未确认”；隐藏外部浮层的应用依然不能从一次绘制推断最终画面。既有使用情况权限和应用监控均缺失时，入口提示准确的权限原因。

## 验证材料

原始材料在本机已忽略的 `.android-local/device-analysis/`：`orientation-app-uid-probe.txt`、`orientation-matrix.json`、`fan-tabs-after.json`、`shader-gamehub-layout.json`、`shader-usage-probe.txt`。主应用使用原签名保留数据覆盖安装；测试 fixture 单独卸载。

本地验证：Debug/Release 构建、Android 单元测试 24 项、温控协调器 228 项及传感器回归、硬件事务 163 项、手柄与风扇队列、Home/Back、完成通知、架构／数据库／362 项多语言资源检查。最终 Debug/Release 构建和 Lint 通过，Lint 为 0 错误、90 项警告；24 项单测无失败或跳过。CONFIG 开关实测关闭时恢复智能并退出风扇服务，再开启可恢复自动停转，新说明完整可见。

可复跑方向矩阵：先安装 `tools/shader-validation` 的 fixture 并在 CONFIG 选择握持模式，然后执行 `tools/android python3 tools/orientation-device-regression.py --serial <serial> --output <ignored-report.json>`。使用情况回退运行验收命令见 [Shader 验收工具](../tools/shader-validation/README.md)。
