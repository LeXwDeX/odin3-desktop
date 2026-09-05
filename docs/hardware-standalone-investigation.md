# 无电脑硬件控制调查（2026-09-05）

用户要求正式使用时无需连接开发电脑，重启后仍可使用性能、风扇和手柄灯。以下记录仅表示已经取得的证据，不表示该要求已经实现。

## 本次实机证据

- 通过新配置的项目 ADB 确认 Odin3 `a782c9a1` 在线且已授权，Android 15，固件 `Odin3_V1.0.0.187_20260616_193307_user`。
- 初次检查 `pidof odin-hardware-bridge` 无结果，现有认证桥未运行。
- 读取原厂 `/system/app/OdinSettings/OdinSettings.apk`，SHA-256 为 `fdcac92b4bc20370372090ac10a896fd7e4742c89f3a92f561c645927866cdf6`，与旧分析记录一致。本地材料位于 `.android-local/device-analysis/`，不提交 APK 或反编译源码。
- `ExternalControlManager` 注册 `SettingsController` 服务；事务 1 按名称返回 `FanProvider`、`LightProvider`、`PreformanceProvider`（原厂拼写）。当前 APK 的后两者方法为空实现，不能凭 Binder 返回成功认定已控制硬件。风扇实现有实际设置逻辑，仍需普通应用进程实测。
- shell 与 `run-as com.odin.desktop` 的 `service check SettingsController` 都能发现服务。但 run-as 的 SELinux 域为 `runas_app`，不等同于真正应用进程；这不能证明正式 APK 可调用。
- `SettingsSwitchesProvider` 只注册系统导航及工厂设置开关，不提供这三个硬件控制的统一读写接口。
- 原厂 Manifest 导出 `MainSettingsActivity`、亮/暗对话框设置页与风扇曲线配置页。它们是可继续验证的原生 UI 入口，尚未验证能精确打开三个目标设置。

JADX 全 APK 反编译报告 4 处错误；上面涉及的类已直接读取，但不能据此排除其他组件中存在可用接口。没有完成全固件接口审计，没有修改厂商包、Root 或系统分区。

## 当前恢复与交付边界

本次项目环境完成 Debug 构建、桥接 110 项自检、风扇策略 91 项检查和三个方法级回归。使用 `manage.py start` 恢复桥接并用 `status` 验证认证连接。管理器报告未修改硬件设置，原桥与配对文件在各自设备私有目录按 `.bak.20260905T143052225000Z` 备份，令牌未导出。

恢复桥接只是让现有安装重新连接，不是重启持久化修复。本次新构建 APK 尚未覆盖安装，按钮与物理硬件尚未重新验收。正式方案需继续选择并验证原生控制 UI，或掌机内无线调试激活；不可将临时 ADB 启动当成独立使用问题已解决。
