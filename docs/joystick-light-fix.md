# 摇杆灯开关闪回

2026-09-06 用户反馈：开关操作后，关闭状态有时短暂显示开启，再回到关闭；尚未区分按钮跳变与实体灯闪烁。

已确认代码竞态：`toggleJoystickLight()` 先乐观显示目标值，延迟 150ms 才写硬件，但风扇通知、页面加载等触发的 `refreshHardwareStates()` 会不加校验地回填旧灯光值。旧值还可能成为下一次开关取反的依据。旧实现取消灯光 Job 也不能撤回已经送入 Binder 的同步写入。

现在灯光操作拥有独立的请求版本和待办目标：主线程更新选择，后台串行完成硬件写入，快速输入只保留最后一个待办目标。刷新在没有待办操作且读回版本仍有效时，才在主线程发布状态；失败会校正到实际状态并提示，后续操作仍可执行。灯光颜色继续独立处理，UI 布局与风扇策略没有变化。

`tools/android python3 tools/cooling-ui-regression.py` 直接提取生产方法，以真实协程和单线程 Main dispatcher 验证延迟读回、写入交错、失败恢复及连续 1,001 次新输入。新增四组灯光回归通过；`--unguarded-light-variant` 只在临时编译副本中移除保护，会在“Observer replaced a pending light selection”处按预期失败，证明确实检出了旧问题。

这些测试证明应用状态与请求队列的一致性，不能证明实体灯的瞬时发光行为。若实体灯仍闪烁，需要继续核对原厂灯光服务的时序。

修复版 `0.1.1 / 1002` 已在 Odin3 上保留数据覆盖安装。通过应用底部按钮执行关闭→开启→关闭，界面分别显示开启/关闭，`joystick_light_enabled` 与 `joystick_handle_light_enabled` 分别读回 `1,1` / `0,0`；结束恢复初始关闭。该次操作没有 `OdinHardware` 警告或 `AndroidRuntime` 错误。Debug/Release 构建、24 项单元测试通过，Lint 无错误（保留 90 条警告）。
