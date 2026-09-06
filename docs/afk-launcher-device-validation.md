# 新应用排序、息屏挂机与原厂桌面依赖联合实机验证

日期：2026-09-06。设备：Odin3 / Android 15，固件 `Odin3_V1.0.0.187_20260616_193307_user`。安装版本：0.1.2 / 1003，Debug，代码提交 `4deb3a7`（包含 `c9dd43f` 新安装排序修复）。本记录与“确认原系统桌面依赖”的静态结论联合验收。

## 更新与数据保留

已比较设备旧 APK 与本地 APK 的签名证书，匹配后使用 `adb install -r` 覆盖成功。更新前备份旧 APK 和桌面 databases/shared_prefs/files/no_backup 到本机忽略目录 `.android-local/afk-combined-validation/`（`before.apk`、`app-data-before.tar`）。未卸载或清理桌面与游戏数据。

## 实机结果

| 场景 | 结果与证据边界 |
| --- | --- |
| 更新前用户正在挂机 | 遮罩可操作，双击退出成功；《终末地》仍是 topResumedActivity 和 mCurrentFocus，设备 Awake，挂机 partial WakeLock 已持有约 7 分钟。退出后该锁释放。 |
| 新版倒计时 | 从系统快捷面板实际点击磁贴，截图显示“6 秒后进入息屏挂机 / 双击屏幕可取消或退出”，游戏画面可见；随后正常遮黑。 |
| 游戏在遮罩下继续工作 | 《终末地》下载量从 10712.53 MB 增到 11503.72 MB（38.93%→41.80%）；退出后下载速度约 10.68 MB/s。挂机期间窗口焦点仍为 `com.gryphline.endfield.gp/com.u8.sdk.U8UnityContext`。证明该游戏此次资源下载继续运行，不推断所有游戏战斗/帧率/断线行为。 |
| 挂机自身保持亮屏 | 排除 USB 常亮（`stay_on_while_plugged_in=0`），临时把超时从 600000 改为 15000 ms。在自己的桌面上开启挂机，系统 `mHoldScreenWindow` 指向本应用遮罩；挂机约 72 秒后仍 Awake，保持亮屏窗口和 partial WakeLock 仍在。测试后恢复 600000 ms。 |
| 倒计时取消 | 开启 1 秒后双击，再等 7 秒：没有重新遮黑，挂机 partial WakeLock 不存在，游戏仍为前台。 |
| 新应用最左侧 | 确认独立测试包不存在后安装 `com.odin.desktop.validationtarget`，实际【全部应用】截图中它位于最左侧。测试后仅卸载该测试包。未在本轮另行通过 Google Play 下载新应用；两种来源共用的排序路径已有单元测试。 |

## 禁用原厂桌面与重启

原厂 `com.odin.odinlauncher` 初始 `enabled=0`（默认），系统默认 HOME 已为 `com.odin.desktop/.ui.MainActivity`。临时仅对原厂桌面执行 `disable-user --user 0`，没有禁用 OdinSettings、SystemUI 或系统硬件服务。

- 禁用后 Home 返回本应用，最近任务可见；实际最近任务组件为独立的 `com.android.launcher3/com.android.quickstep.RecentsActivity`。
- 禁用后、整机重启后分别运行普通应用 UID 10119 / untrusted_app 内的 `HardwareProbeInstrumentation -e verify_controls true`。性能 1→2→0、风扇 5→4、两组灯光、颜色、充电限制、充电分离、飞行模式往返全部成功；两轮 restoration 的八项均为 true。低温关闭风扇分支本次未触发，不能声称本轮覆盖手动关扇。灯光是配置读回，不是本轮现场肉眼确认。
- 探针直接读取受限 sysfs 出现 EACCES 是预期权限边界；实际产品使用的 PServerBinder 通道可读性能、风扇、转速与 PWM，完整控制验证没有 failure。
- 用户明确允许暂时中断下载和重启后，停止游戏进行完整硬件测试并重启。`sys.boot_completed=1`，原厂桌面禁用状态保留，系统进入自有 MainActivity，HOME 解析正确；最近任务组件和独立 Odin 设置 Activity 均能启动。
- 重启后重新打开《终末地》，截图显示续传到 13601.58 MB / 27519.19 MB（49.43%），速度 5.50 MB/s，没有从头下载。

启动 crash 缓冲区另有系统 `init` / `qcrosvm trustedvm` 的 SIGABRT，后者报告 `set fw name ioctl ... Bad file descriptor`。本轮未做原厂桌面启用时的同条件重启对照，不能判断它与禁用桌面是否有关；实际开机、HOME 与上述控制均完成，但不宣称系统日志完全无异常。

结论：当前固件下，本次已测的桌面、挂机及硬件控制不需要原厂 `com.odin.odinlauncher` 启用。仍依赖固件硬件服务、OdinSettings/SystemUI 等其他系统组件；不推广为可以禁用所有 Odin 包，也不保证其他固件相同。

## 恢复与交付

原厂桌面已通过 `pm default-state` 恢复原始默认启用状态，默认 HOME 保持本应用；休眠超时恢复 10 分钟，硬件配置恢复，测试应用已移除，CLI instrumentation 已停止。最后重新开启本应用挂机，让《终末地》继续下载。

原始窗口/电源/前台报告、两轮硬件报告、截图、APK 和备份均在 `.android-local/afk-combined-validation/`，不提交到 Git。长达数小时、物理电源键关屏及所有游戏行为不在本轮验收范围。

## 挂机底部手势导航条修复

同日用户报告挂机底部留有白色横条。实机截图复现：黑色遮罩覆盖游戏，但系统手势导航条仍亮。挂机浮层现声明沉浸式系统 UI 标志，并在 Android 11 及以上挂载窗口后通过 `WindowInsetsController` 请求隐藏系统栏，允许边缘滑动临时呼出（[Android 官方说明](https://developer.android.com/develop/ui/views/layout/immersive)）。保留不获取焦点、保持亮屏和双击退出的原有行为；不修改系统导航方式或全局沉浸策略。

Debug 构建与现有 `AfkOverlayServiceTest` 在 API 32 / 35 共 6 项检查通过。核对新旧 APK 签名一致后保留数据覆盖安装。在同一 Odin3 / Android 15 上，从快捷磁贴启动挂机，倒计时后截图确认底部白条消失；窗口焦点和 focused app 仍为《终末地》。双击退出后显示原游戏的服务器选择界面，随后重新开启挂机。本轮没有实际战斗验收，也未验证其他固件或三键导航。

本轮原始截图和窗口报告为本机忽略目录 `.android-local/afk-nav-*`；安装前 APK 备份为 `.android-local/afk-nav-before.apk`。
