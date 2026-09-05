# 2026-09-06 剩余问题与实机验收

本轮范围：Issue #4 的滤镜状态、Issue #2 的启动复验、历史设备待验项，以及用户新确认的顶部电池/风扇信息区。完整跨应用游戏帧接入仍属于后端能力边界，不把截图预览冒充已完成的实时 Shader。原先暂停的全局 UI 调整和入口迁移不在本轮扩展。

## VIDEO SHADER 状态

启用意愿、配置保存和当前运行证据分别处理。调节页的既有目标行展示当前效果状态；开关标题明确是“启用意愿”，保存结果只说已保存意愿。下拉磁贴使用同一运行状态，未取得游戏最终画面的验证时不点亮成“已生效”。

- 使用固定目标、配置代次及主线程发布，旧数据库回包和旧绘制回调不能覆盖新目标/新配置。
- 组合需要游戏像素输入时显示“仅预览”；没有选择效果、系统版本不支持、缺少悬浮窗权限、前台未知、控制面板/系统界面暂停及运行失败各有对应状态。
- `addView` 成功不产生生效结论。硬件画布完成绘制后，只记录“CRT 遮罩已绘制 · 游戏效果未确认”。绘制/挂载失败会移除覆盖层；尚未获得绘制回调时有界转为未知。
- 监控服务丢失、目标切换、返回桌面、系统面板、息屏和权限失效会清理或暂停覆盖层。控制面板关闭后重新确认真实前台，不恢复上一个游戏的成功状态。保存按钮也不预先假定 `startActivity` 已让游戏进入前台。
- 保存队列复用应用配置仓库；调节面板的旧保存回包不能逆转新一次开关选择。

实机使用独立测试 APK `com.odin.desktop.validationtarget`，不读写用户游戏存档。允许浮层时画面可见 CRT 网格；同一个测试应用调用 `Window.setHideOverlayWindows(true)` 后网格消失，但 Android 仍可能保留已经绘制的 View 信息。两种情况下运行状态均明确“未确认”，不会谎报游戏已生效，也不猜测原因是反作弊。这个差异证明仅用 View 附着、可见标志或绘制回调无法完成游戏输出验收。[Android 官方浮层限制说明](https://developer.android.com/security/fraud-prevention/activities#HIDE_OVERLAY_WINDOWS)

自动实机检查覆盖：关闭、关闭时从系统窗口返回、兼容 CRT 遮罩、主动屏蔽浮层、FXAA 不支持组合、未选择效果、撤销/恢复浮层权限、前台丢失/恢复、返回桌面。系统覆盖状态与展示文案分开记录，避免滤镜关闭或不支持时漏掉窗口恢复。最终矩阵通过，测试配置和原权限均恢复。原始报告及对照图在本机忽略目录 `.android-local/device-analysis/completion-shader-final.txt`、`shader-allowed.png`、`shader-blocked.png`。

## 顶部电池与风扇

按用户本轮明确要求，缩小 TAB 内边距和可占宽度，在设置左侧固定放置电量、充电状态、风扇 RPM 和 PWM 百分比。长分类名省略、分类列表仍可滚动和通过 L1/R1 切换，状态区不参与手柄焦点。底部 Dock 的几何布局没有改变。

电池使用系统电量广播，充电/已充满/未充电独立显示。风扇读取已验证的原厂服务接口，2 秒一次；仅桌面 STARTED 时采样，离开即取消并清空旧值。读取失败显示“—”，不伪造零转速。PWM 是驱动占空比，不能当作转速百分比。

顶部中文、英文、日文均完成实机查看，状态栏和设置按钮没有被长分类名挤开。分类保留原有的选中项滚动逻辑，实机通过肩键切换分类。设置语言原为“跟随系统”，检查后从应用设置恢复，重启后系统读回为 `[]`。额外修复了 Dashboard 存储说明缓存沿用旧语言的问题，缓存现按语言失效；用户自定义分类名称保留原文。

自动风扇补验中，充电且默认性能时开启策略，观察到顶部 `0 RPM / PWM 0%`、底部蓝色“关闭”与驱动 state/duty/speed 全零一致；温度变化后策略恢复散热。自动策略开启期间连续切换三次性能，最后回到默认档。测试结束关闭自动策略，确认性能 `0`、风扇 `4`（智能）、自动开关 `false`；重启后再次读回相同设置。该补验不宣称穷尽所有手柄交错或温度条件。

挂机磁贴补验：亮屏时黑色层显示双击退出提示，双击后 `AfkOverlayWakeLock` 释放。测试开始时曾遇到设备已经休眠的黑屏，唤醒后重新检查；不把休眠截图当成浮层绘制证据。该测试不验证任意游戏长时间后台帧率。

Home/Back 实机各 20 次：Home 仅发生 pause/resume，没有额外 stop/start；Back 没有引发生命周期变化，进程始终处于桌面前台。报告为 `.android-local/device-analysis/completion-home-back.json`。

## 启动与 Logo

当前默认 HOME 是用户已选择的 Odin Desktop。本轮没有再次更改默认桌面角色。真实冷启动正常进入可操作桌面。

本轮另完成一次整机重启：`sys.boot_completed=1`，系统以 `MAIN/HOME` Intent 恢复 `MainActivity` 为前台，设置按钮可打开。`PServerBinder` 由固件恢复，应用顶部继续读取约 5100–5700 RPM、PWM 24%；未运行旧硬件桥。重启时系统弹出 USB 用途提示，取消后正常显示桌面，不属于 Logo 卡住。最终图为 `.android-local/device-analysis/completion-final-header.png`。独立测试 APK 已卸载，主应用数据保留。

Debug 专用 `StartupProbeInstrumentation` 在本应用进程内临时替换 `ServiceManager` 缓存，分别模拟服务离线、异常回复和 2.5 秒超时，不终止、不修改原厂服务。四种场景均提交桌面帧，返回键和硬件失败后的主线程响应通过：

| 场景 | 探针从启动到一次确认绘制 |
| --- | --- |
| 正常 | 830 ms |
| 服务离线 | 847 ms |
| 异常回复 | 992 ms |
| 超时 | 1004 ms |

这些是调试探针的单次采样，不是性能基准。普通 `am start -W` 另一次冷启动 `TotalTime=367 ms`。探针结束后恢复进程内缓存；Release 不含探针。

**2026-09-06 结案更新：用户确认 Issue #2 的原因为应用自启动但未注册为桌面，与已注册的系统桌面相互争抢，并明确要求关闭。** 该问题随 #1 移除主动拉起、改为系统 HOME 注册并由用户选择默认桌面的修复结案。上述冷启动、重启和硬件失败测试是修复后的验证记录；原始卡住未在本轮复现，历史日志没有留下该次线程堆栈。结案原因来自用户确认，早先“继续待定位”的状态不再适用。

## 本地检查

Debug、Release、Lint 均通过；Lint 为 0 个错误、89 个警告（包含既有依赖更新、未使用资源以及调试探针的私有 API 提示，不宣称零警告）。9 项 JUnit 测试通过，其中 3 项验证 Shader 状态及全部需要像素输入的组合。362 个语言资源键、占位符、数据库迁移/DAO/预设回归通过；实际源码 Home/Back 与 3,000 次混合硬件按键合并回归通过。

## 复验工具与边界

```sh
tools/android ./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:testDebugUnitTest --offline
tools/android python3 tools/architecture-regression.py
tools/android python3 tools/home-back-regression.py
tools/android python3 tools/cooling-ui-regression.py
tools/android python3 tools/shader-validation/build.py
```

测试应用、Debug 探针及其恢复步骤见 [Shader 验收工具](../tools/shader-validation/README.md)。探针必须检查报告没有 `error` 且恢复成功，不能仅看 `adb` 退出码。启动 instrumentation 会重启目标应用并暂时影响无障碍绑定；本工具不会为未授权用户开启监控。普通 APK 使用无需这些开发工具。

最终复跑曾与仍运行的 Android CLI 布局工具争用 UiAutomation，在调试探针结束时触发 disconnect 异常；停止 CLI 开发服务后全矩阵通过。该次无有效报告的运行不计入通过结果，异常也不作为原始 Logo 故障的根因证据。
