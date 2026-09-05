# Odin 3 原厂硬件服务接入

## 当前方案（2026-09-06）

性能、风扇、摇杆灯、充电和飞行模式由应用直接调用固件自带的 `PServerBinder`。这个 Binder 服务由固件注册，实机对应的 `pservice` 进程使用 root 身份；我们的应用仍是普通应用 UID，不启动 root 进程，不修改系统权限策略，也不复制厂商可执行文件。

调用路径：

```text
现有 Dock / 自动风扇策略
  → HardwareController
  → HardwareControlClient
  → HardwareOperations（固定操作、串行事务、核验与回滚）
  → PServerBinder（原厂系统服务）
  → OEM Settings / 性能属性 / 系统 connectivity 命令
  → 原厂观察器及驱动
```

`HardwareControlClient` 在每次调用时重新取得可用 Binder。原厂服务暂不可用时返回错误，后续调用重新获取，不依赖旧连接、配对文件或电脑。旧 socket 客户端已删除，APK 不再声明原桥所需的 `INTERNET` 权限。`tools/hardware-bridge` 只保留开发诊断和共享回归用途。

### 为什么不复制服务作为 fallback

原厂 `pservice` 的权限来自系统身份与 SELinux 配置。将二进制复制进普通 APK 不会继承这些权限，不能成为独立备用服务。项目内置的是调用协议、固定操作和恢复逻辑。真正独立的另一条控制通道仍须有合法系统授权；当前版本不要求用户安装 Shizuku、无线调试配对或连接电脑激活。

### 协议与状态核验

- 原厂事务 0 接收 `[命令, 等待结果标志]`，返回 byte array。实机发现它只返回首行，过长命令会失效；适配器将退出码与输出合并为一行，限制为 255 字节，逐参数引用，并给命令设置 2 秒期限。多字段写入拆成短命令，事务整体仍检查和回滚。
- 应用仅接受固定的性能、风扇、灯光、充电、飞行模式及只读遥测操作；不接受用户提供的任意命令、路径、属性或包名。
- 性能同时检查 `performance_mode` 与真实 `persist.vendor.debug.mode`。性能变化引起的 OEM 风扇异步重置，仍按既有协调逻辑处理。
- 风扇检查配置与 PWM state/duty/period；独立读取 `speed` 的 RPM 和 PWM 占空比。不可读或无效值返回未知，不伪装为 0。PWM 百分比不是转速百分比；原厂最高固定档 duty=25000、period=50000。
- 灯光与充电检查 OEM 配置读回。配置成功不等同于实体 LED 输出、真实电流或空气流量的测量。
- 飞行模式走 `cmd connectivity airplane-mode`，使系统处理模式切换；失败恢复前值，随后再次核对。
- 用户要求本轮保持 UI：最终 Dock、顶部栏、设置和布局文件与修复前一致。RPM/PWM 仅保留读取与诊断能力，显示区域另行决定。

## 实机与构建验证

目标：Odin3，Android 15，固件 `Odin3_V1.0.0.187_20260616_193307_user`。

- 实际应用进程 UID 10119、`untrusted_app` SELinux 域、target SDK 35。不是以 `run-as` 域或 shell UID 的成功推断普通应用可用。
- 只读探针能通过原厂接口取得真实性能档位、风扇配置、RPM 与 PWM。
- 应用内固定验收完成性能 1→2→0（保持智能散热）、风扇最高/智能及低温正常性能下的关闭/恢复、两组灯光开关、颜色、80%/5V、充电分离和飞行模式往返；原配置全部恢复。
- 真实 Dock 按钮验收：性能属性与最终风扇联动一致；手动风扇 4→5→0，实测驱动与转速分别约 `1/12500/5400 RPM`、`1/25000/8700 RPM`、`0/0/0 RPM`；灯光、5V 和飞行模式开关往返读回成功。验证异步操作时等待完整事务，不能只在性能镜像刚变化时检查下一步。
- 临时撤销 `WRITE_SECURE_SETTINGS`、`DUMP` 和 WRITE_SETTINGS AppOp 后，固定控制与恢复全部通过。随后恢复测试前授权，保留 Dashboard 原有诊断能力。
- 设备重启完成后再次验证：旧桥进程不存在、无线调试关闭，撤销上述额外授权的普通应用仍可完成全部控制并恢复。重启期间 USB 曾短暂多次枚举；重连后原授权已恢复。
- 进程重开可重新连接原厂服务。未主动终止固件 root 服务；这不等于验证所有厂商服务故障情形。
- Debug、Release 构建与 `lintDebug` 通过；发布 manifest 无 INTERNET 权限及调试 instrumentation。共享事务/协议 142 项 JVM 检查、风扇协调 91 项检查与实际源码 UI 队列回归通过。
- 灯光软件配置已验证；用户现场确认“实体灯正常跟随亮灭”。此项是现场观察，区别于软件配置读回。

诊断结果保存在本地忽略目录 `.android-local/device-analysis/`，包括 `native-controls-no-grants.txt`、`native-controls-after-reboot-no-grants.txt`、`native-ui-verification.json` 和 `native-ui-performance.json`。未提交厂商 APK、反编译源码、签名密钥或配对令牌。

上述结论仅对已验证固件成立。`PServerBinder` 是厂商私有接口，不能据此承诺其他机型或未来固件通用。应用的统计、无障碍及旋转功能权限与硬件控制权限分开处理。

## 前期调查（2026-09-05）

原厂 `OdinSettings.apk` SHA-256 为 `fdcac92b4bc20370372090ac10a896fd7e4742c89f3a92f561c645927866cdf6`。`SettingsController` 中 `FanProvider` 有实际实现，普通应用实测可读转速；`LightProvider` 和 `PreformanceProvider` 的对应方法为空，单凭名称或 Binder 返回成功不能认定能控制。

继续沿原厂调用代码找到其实际使用的 `PServerBinder`，才完成当前直接控制方案。`SettingsSwitchesProvider` 及原厂设置页不是最终控制后端。早期通过电脑临时启动桥接仅用于排查，不能当作独立使用修复。
