# 方向与启动设备验收

使用独立的 `com.odin.desktop.validationtarget` 测试应用检查方向请求，不修改游戏或存档。产物保存在忽略目录 `.android-local/device-validation/`，使用项目调试签名。

```sh
tools/android adb devices -l
tools/android python3 tools/device-validation/build.py
tools/android adb -s <serial> install -r .android-local/device-validation/target.apk
# 先在 CONFIG 中选择握持横屏；矩阵覆盖正反/传感器横屏和三种竖屏请求。
tools/android python3 tools/orientation-device-regression.py --serial <serial> --output .android-local/device-validation/orientation.json
```

测试结束会停止测试应用并返回桌面。确认它确实是本次安装的独立测试包后，可卸载 `com.odin.desktop.validationtarget`；保留主应用和用户数据。

Debug APK 还提供应用内硬件启动故障探针：

```sh
# 若 Android CLI 正在占用 UiAutomation，先停止开发工具。
tools/android adb -s <serial> shell am force-stop com.android.cli.interact.instrumentation
tools/android adb -s <serial> shell am instrument -w -e fault offline com.odin.desktop/com.odin.desktop.hardware.StartupProbeInstrumentation
# fault 支持 read / offline / malformed / timeout。
```

启动探针只替换调试 APK 本进程的缓存，不改厂商系统 Binder 注册表。应在 35 秒内结束，并核对报告无异常且 `local_cache_restored=true`；超时或仅有进程退出码不算通过。运行时不要并发调用另一套 instrumentation；结束后正常重开桌面。Release APK 不包含探针。

这些开发工具不属于应用运行依赖。旧版 Shader 专用验收已移除，历史范围见 [记录](../../docs/shader-removal.md)。
