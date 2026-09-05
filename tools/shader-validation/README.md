# Shader 与启动设备验收

仅用于开发验收。测试目标不包含用户数据，不调用游戏、不绕过其保护。产物位于忽略目录 `.android-local/shader-validation`，使用项目调试签名；不要提交 APK 或 keystore。

```sh
tools/android python3 tools/shader-validation/build.py
tools/android android install --device=<serial> --apks=.android-local/shader-validation/target.apk
# layout/screen 使用的 CLI 服务若仍占用 UiAutomation，先停止该开发工具。
tools/android adb -s <serial> shell am force-stop com.android.cli.interact.instrumentation
tools/android adb -s <serial> shell am instrument -w -e verify true com.odin.desktop/com.odin.desktop.shader.ShaderProbeInstrumentation
```

先确认设备已授权、亮屏，主应用为 Debug 构建且已启用应用监控。探针只对固定测试包保存/恢复 Shader 配置，临时撤销/恢复应用浮层权限。由于 Android 启动 instrumentation 会停止应用并可能将其无障碍服务标记为崩溃，探针使用 `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`，且只重新绑定已经启用的本应用监控组件；原组件列表保留。未预先开启监控时测试失败，不代替用户授权。

默认 instrumentation 不传 `verify=true` 时只读。传 `pause_at=allowed` 或 `pause_at=blocked` 可在对应画面停留 30 秒供截图。必须检查最终 JSON 无 `error`、`config_restored=true` 及权限恢复字段。程序中断后先核对浮层权限/监控组件列表与测试包配置，再继续测试。

探针运行期间不要同时调用依赖另一套 instrumentation 的 `android layout`；必要时用 `adb exec-out screencap -p` 保存画面并立即人工查看。若仅得到 `Process crashed`，应先读 crash buffer：本设备曾因两套开发工具争用 UiAutomation，在 `Instrumentation.finish` 的 disconnect 阶段异常。该工具启动失败不计为 Shader 验收通过，也不能据此归因为用户遇到的 Logo 故障。

```sh
# 仅替换调试 APK 本进程的缓存，不修改厂商系统服务。
tools/android adb -s <serial> shell am instrument -w -e fault offline com.odin.desktop/com.odin.desktop.hardware.StartupProbeInstrumentation
# fault 支持 read / offline / malformed / timeout。
```

启动探针应在 35 秒内结束；超过时记录报告缺失，不宣称测试通过，停止本应用后正常重开。`local_cache_restored=true` 只表示应用自己的缓存已恢复；不涉及系统 Binder 注册表。Release manifest 不包含这些 instrumentation。

结束后从系统卸载本次新建的 `com.odin.desktop.validationtarget` 测试 APK，保留 `com.odin.desktop` 及用户数据。最后重新打开桌面并核对前台监控恢复。验收结果见 [记录](../../docs/completion-validation.md)。
