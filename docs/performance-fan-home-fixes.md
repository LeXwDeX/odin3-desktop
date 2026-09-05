# 性能、风扇与 Home/返回键修复记录

## 2026-09-06：Logo 问题结案

用户确认 [Issue #2](https://github.com/LeXwDeX/odin3-desktop/issues/2) 原因为应用自启动但未注册为桌面，与已注册的系统桌面相互争抢，并要求关闭。处理由 #1 的修复覆盖：移除开机主动拉起界面，注册系统 HOME，由用户选择默认桌面。冷启动、系统 HOME 启动和整机重启均已通过；结案原因采用用户确认，下面较早阶段的“根因待确认”保留为历史记录。

## 2026-09-06：硬件服务直接接入

当前硬件后端已由临时 ADB 桥改为原厂 `PServerBinder`。性能、风扇、灯光、充电及飞行模式的普通应用直控、额外权限撤销及重启验证见 [接入记录](hardware-standalone-investigation.md)。本轮最终没有 UI 布局变化；下面较早段落中“需启动桥/无电脑未解决”的状态属于当时记录，不再作为当前部署步骤。


## 2026-09-06：默认桌面注册与升级验证

对应 [Issue #1](https://github.com/LeXwDeX/odin3-desktop/issues/1)。开机接收器已删除主动启动 MainActivity 的分支，三个既有开机广播仅尝试恢复温控服务。设置页面和手柄导航移除了独立的开机自启开关；旧 `boot_auto_start_enabled` 偏好不再读取。

实机另发现默认桌面按钮曾立即返回：系统日志为 `RequestRoleActivity: Package name cannot be null or empty: null`。原实现通过普通 `startActivity` 发起 HOME 角色请求，系统无法取得结果调用方。现在由 MainActivity 注册 `ActivityResultContracts.StartActivityForResult`，发起请求并在返回时刷新角色状态；已持有角色时保留系统默认应用设置入口。[Android RoleManager 文档](https://developer.android.com/reference/android/app/role/RoleManager#createRequestRoleIntent(java.lang.String)) 要求按 Activity Result 方式发起此请求。

本次用原调试 keystore 构建并保留数据覆盖安装，未卸载应用或清空数据。首次覆盖安装前后核对私有目录中的 48 个文件，内容全部一致。安装前的数据库、偏好、文件及 no_backup 目录备份在本机忽略目录 `.android-local/device-analysis/home-fix-data-before-20260906.tar`；旧 APK 为同目录的 `desktop-before-home-fix.apk`。这些原始材料和签名私钥不提交。

Odin3 / Android 15 实机结果：

- 冷启动能进入桌面，设置页可正常操作，自启开关已移除。
- 点击默认桌面卡片能显示 PermissionController 的系统选择窗口；取消后角色仍为原厂 `com.odin.odinlauncher`。
- 原厂桌面被选为默认时正常重启：HOME 角色与 Intent 解析均为原厂桌面，重启日志中没有本应用 MainActivity 启动记录。
- 在系统窗口中选择 Odin 启动台后，HOME 角色和 Intent 解析均为 `com.odin.desktop/.ui.MainActivity`；按 Home 正常进入桌面。
- 再次正常重启后，由 `MAIN` / `HOME` Intent 进入 Odin 启动台，界面已绘制且可打开设置，没有复现 Logo 卡死。
- 验收结束后，通过应用的“管理桌面设置”进入系统页面，恢复原厂启动器为默认桌面；HOME 角色及实际前台均已核对。

本地验证：Debug 构建、`tools/home-back-regression.py`、`tools/cooling-ui-regression.py` 通过。Home 回归新增了真实 BootCompletedReceiver 的三种广播、旧自启偏好为 true、温控服务启动异常及无关广播场景，确保没有 Activity 启动；同时检查角色选择返回时刷新状态，保留原有 100 次 HOME/BACK 重放。

上述结果覆盖默认桌面注册和该阶段重启场景。该阶段记录中，Issue #2 原始 Logo 卡死原因仍待确认，性能、风扇仍显示未连接/未读取，Issue #3 无电脑硬件控制要求尚未解决；当时没有启动临时 ADB 硬件桥。后续 #2 结案与 #3 原厂服务接入结果见本文件开头的更新。

## 2026-09-04：交接背景

本次继续处理 Antigravity 留下的未提交修改，保留其他应用管理、滤镜与配置变更。目标设备通过 ADB 重新确认：Odin3，Android 15，序列号 `a782c9a1`。

## 新机器继续开发

本次推送包含接手时的应用分类、设置滚动、滤镜校准修改，以及性能/风扇和 Home/返回修复。这些已有修改一起通过了最终 Debug 构建；本记录的专项验证范围限于性能、风扇和 Home/返回。

```sh
git clone https://github.com/LeXwDeX/odin3-desktop.git
cd odin3-desktop
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
# 安装 SDK platform android-35 和 build-tools 35.0.0；首次构建需要联网下载依赖。
./gradlew :app:assembleDebug
python3 tools/fan-policy/test.py
python3 tools/home-back-regression.py
python3 tools/cooling-ui-regression.py
python3 tools/fan-state-completion-regression.py
python3 tools/hardware-bridge/build.py --sdk "$ANDROID_HOME" --java-home "$JAVA_HOME"
```

需要 Python 3 和完整 JDK 17，无额外 Python 包。三个 Kotlin 方法回归脚本依赖首次 Gradle 构建生成的缓存，支持 `GRADLE_USER_HOME`，未设置时使用 `~/.gradle`。项目内环境可通过 `tools/android python3 tools/…` 运行，见[开发说明](development.md)。协调器测试无需 Gradle 缓存。APK、桥接构建产物、`local.properties`、签名私钥、设备配对令牌和 `/tmp` 原始调试材料均不在 Git 中。

设备重新连接后先运行 `adb devices` 确认目标，再覆盖安装新 APK、按原厂服务接入记录核验控制，完成本文“设备断开时的交付边界”中的三项实机验收。新机器的默认 debug 签名可能不同；需要安全迁移原调试 keystore 才能保留数据覆盖安装，不将私钥提交到 Git，也不通过卸载/清数据绕过签名不匹配。

## 根因与最终行为

SystemUI 的 `FanTile` 观察 `Settings.System.performance_mode`。外部写入默认档时无条件将 `fan_mode` 改为 0；性能档在当前不是智能 4 时改为静音 1；高性能档把关闭 0 / 静音 1 改为最大 5。该回调没有固定延迟，也不受 `is_quick_set_performance_and_fan_enable` 限制。OdinSettings 随后异步写 PWM enable 和 duty。因此仅确认性能属性或 Settings 写入成功，会遗漏风扇已经变化的事实。

原始实机复现为：高性能+最大时 `fan=5,state=1,duty=25000,speed=9300`；切默认后变为 `fan=0,state=0,duty=0,speed=0`。旧桌面成功路径没有刷新风扇显示。

修复规则：

| 用户风扇模式 | 切默认 | 切性能/高性能 |
| --- | --- | --- |
| 手动最大 | 保持最大 | 保持最大 |
| 其他手动档位 | 关闭 | 智能 |
| 自动传感器开启 | 先保持冷却，再按充电、前台、温度决定是否停转 | 保持冷却；开关不被关闭 |

性能和最终风扇目标通过同一个认证事务执行。对于可能降低目标风扇的 OEM 回调，先准备一个冷却档位，再等待明确的 OEM 关闭/静音设置响应，之后设置最终风扇，避免把固定睡眠当成完成信号。配置与 PWM 连续读回；失败时先恢复性能镜像及属性，再恢复风扇，最后一起检查。风扇键相同但 PWM 不一致时，通过另一冷却档重新触发观察器。

应用协调器统一处理手动、性能与自动策略。自动关扇记录所有权，关闭自动只恢复它自己关掉的风扇；手动关闭释放所有权。策略提交前重新校验快照和请求版本，防止旧的默认档判断覆盖新的性能操作。UI 按键立即更新用户选择，后台合并同类快速输入并执行最后一次意图，不显示中间态。旧读回通过版本校验和主线程原子发布保护，不能覆盖新选择；没有新输入后再校准设备状态，执行失败则提示并恢复实际值。外部通知先于 PWM 完成时进行有界重试。

性能操作携带按键当时选定的风扇目标，避免快速执行“手动最大→开自动→切性能→关自动”时，后台因自动开关已合并而重新算出最大档，覆盖用户看到的智能档。自动策略在确认实际风扇变更成功后发送包内完成通知；刷新消费者等待当前用户操作提交后再读回，防止提前消耗唯一的设置通知。

## 500ms 线索的归属

`com.ro.settings.trigger.j` 在 OdinSettings.apk 中，`j.run → n.l → n.D` 是未充电时亮屏后的 500ms 恢复任务，不是性能切档延迟。GameAssistant.apk 的 `0x1b985c` 对应 `FloatingViewService.I(int)` 的游戏风扇配置解析。

当前设备 APK 哈希已与只读分析文件核对：

- OdinSettings：`fdcac92b4bc20370372090ac10a896fd7e4742c89f3a92f561c645927866cdf6`
- SystemUI：`94ea7ac29f067ac597160d1c28d615aaf9d86bc3c0c3be27b38601c7a0bc8e73`
- GameAssistant：`02d96ded4c973347d9dd028e9b168baa229084db2e04acecaffc636c67e5426e`

字节码摘录和分析保存在工作机 `/tmp/odin-oem-analysis/`，没有将 APK 放入仓库，也没有修改厂商组件。

## Home 与返回键

已经可见的 singleTask 桌面收到 HOME 时可能经历 PAUSE→RESUME。旧 `onResume` 每次都扫描应用、刷新硬件、读取无障碍设置并更新滤镜；Dashboard 也会随 PAUSE/RESUME 停止、重启采集。修复把这些操作绑定到真实的 START/STOP 可见性变化。BACK/B 在桌面根页面消费为无操作，弹窗和页面导航仍进入 ViewModel 的返回逻辑；系统/手势返回走同一路径。

本地测试直接编译实际 MainActivity、GamepadKeyHandler，注入计数用的 Android/VM 替身。100 次 HOME 生命周期重放，额外应用扫描、硬件刷新、无障碍读取和滤镜刷新各由 100 次降至 0；Dashboard 额外切换由 200 次降至 0。BACK/B 和系统返回测试覆盖根页、弹窗以及未知按键转交。该测试验证重复工作次数，不等同于整机 CPU 基准。

实机分别发送 60 次 HOME 和 BACK（每次间隔至少 50ms）：

| 场景 | 采样时长 | 桌面 CPU（单核折算） | 生命周期 |
| --- | --- | --- | --- |
| HOME ×60 | 4.921 秒 | 1.42% | 60 pause/resume，0 stop/start |
| BACK ×60 | 4.855 秒 | 1.65% | 无变化 |

实际 CONFIG 弹窗一次 BACK 正常关闭，PID 保持；新版没有新增崩溃。该短窗口数据不是旧新版严格 CPU 对照，不能据此计算提升百分比。旧版未限速 HOME 突发曾引发 Binder transaction 错误和桌面进程重启，旧版后续采样又受到 ResolverActivity 干扰，均没有作为有效 CPU 基线。

测试还修复了设备首选 Home 记录：Home role 已为 `com.odin.desktop`，但 MAIN/HOME Intent 解析到系统 ResolverActivity。执行 `cmd package set-home-activity com.odin.desktop/.ui.MainActivity` 后与已有 role 一致；后续覆盖安装后已再次确认解析为 Odin Desktop。原状态和结果保留在本记录中。

## 验证与恢复

可重跑命令（JDK 17）：

```sh
export JAVA_HOME=/path/to/jdk17
python3 tools/fan-policy/test.py
python3 tools/home-back-regression.py
python3 tools/cooling-ui-regression.py
python3 tools/fan-state-completion-regression.py
python3 tools/hardware-bridge/build.py --sdk /path/to/android-sdk --java-home /path/to/jdk
./gradlew :app:assembleDebug --offline
# 以下命令需要设备重新连接，且测试前先切为手动风扇模式：
python3 tools/fan-device-regression.py --serial a782c9a1 --output /tmp/odin-fan-matrix.json
```

桥内 `Process.waitFor(timeout)` 在本设备上使每条子命令增加约 100ms 的量化等待。改为原生等待配合独立 2 秒超时后，同一组各 5 次认证只读请求的中位数：性能读取 102.3→4.4ms，风扇读取 203.2→10.2ms。该指标是读取延迟，不是完整 UI 切换时间。原始记录 `/tmp/odin-oem-analysis/BRIDGE_LATENCY.md`。

本地协调器 91 项检查、桥接 110 项检查通过；最终 APK 构建通过。UI 队列 8 组方法级回归通过：直接提取当前生产方法，使用真实协程与单线程 Main dispatcher，覆盖立即显示、1,000 次同类按键合并、3,000 次混按队列有界、旧事务/读回与新选择交错、失败校正及失败后保留后续操作。两个临时旧逻辑对照都按预期失败：恢复后台重算风扇会得到 MAX 而非 SMART；移除读回版本保护会把新选择改回旧值。自动策略完成通知测试直接提取实际方法，验证通知在成功写入后发送、限定包范围，且无变化、旧请求与失败均不假报成功。这些替身测试没有验证实机 Binder、Android 广播送达或 PWM。

原始实机复现在新桥上通过：高性能+最大切回默认，`fan=5,state=1,duty=25000` 保持，采样转速约 9000–9300。

六方向矩阵 `0→1→2→0→2→1→0` 全部通过。每次回复后立即、再等 0.8 秒、再等 4.2 秒采样，均为 `fan=5,state=1,duty=25000`，转速 9000–9300。覆盖所有六个有向切换及一个完整 OEM 4 秒循环。结果 `/tmp/odin-fan-matrix.json`；Home/Back 结果 `/tmp/odin-home-back-after.json`。

PWM 是控制输出的证据；最大档转速还单独采样。智能模式为 OEM 软件温控循环，每 4 秒根据曲线更新，允许 duty=0；本次配置/PWM读回不能证明所有温度条件下的曲线正确或永久运行。未覆盖固件升级后 OEM 行为变化。

### 2026-09-06 重连补验

下方“设备断开时”的内容保留为历史记录。本轮已使用原调试签名覆盖安装当前版本，完成原厂服务接入、自动风扇 UI 刷新和重启补验：默认性能、充电条件下自动停转时，Dock 蓝色“关闭”、新增顶部 `0 RPM / PWM 0%` 与驱动读回一致；温度变化后恢复散热。自动开启时连续切换性能并回到默认，结束后关闭自动，恢复智能风扇，读回性能 `0`、风扇 `4`、自动 `false`；重启后仍一致。

Home/Back 各 20 次补验没有多余 stop/start。整机重启后系统以 HOME Intent 进入本应用，原厂 `PServerBinder` 和顶部风扇读数恢复，没有启动旧桥。此次测试未复现原始 Logo 故障，也不宣称已验证所有快速交错或所有温度条件。具体证据、测试工具与边界见 [本轮验收记录](completion-validation.md)。

### 设备断开时的交付边界（历史）

用户已拔走设备。断开前已安装立即显示、无“切换中”和加速硬件读取的版本。之后补充的主线程原子发布、性能携带明确风扇目标、自动策略完成通知及等待待办结束后刷新，只完成了本地回归与 APK 构建，尚未安装到设备。

手动风扇与性能联动的触摸验证已通过。自动传感器验证曾观测到设备已为关闭、界面仍为智能；随后发现刷新通知可能在待办期间被提前消耗，已补上述刷新路径。原 UI dump 脚本复用输出路径也可能影响该次观测，不能认定这是唯一根因。重连后需要用新的独立 dump 路径确认：默认档自动停转显示蓝色关闭、自动切换完成后刷新，以及性能/风扇/自动开关快速交错时最终显示与设备一致。

最终本地产物：`app/build/outputs/apk/debug/app-debug.apk` 和 `tools/hardware-bridge/build/bridge.jar`。设备复验记录以本文件的实机段落为准，不将本地测试通过视为已安装。

恢复材料：

- 原设备桌面 APK：工作机 `/tmp/odin-desktop-before-20260904.apk`，覆盖安装保留应用数据。
- 接手时未提交差异：工作机 `/tmp/odin-antigravity-handoff.patch`。
- 原桥：设备 `/data/local/tmp/odin-hardware-bridge/bridge.jar.bak.20260904T094031228234Z`。原配对文件留在原设备私有目录，同后缀备份，未导出或记录内容。可用 `manage.py start --jar` 重新配对恢复旧桥，不直接复制或显示令牌。
- 风扇矩阵测试的基线记录在输出 JSON 中，结束时已恢复。后续自动 UI 测试的最后一次设备读回为默认性能 0、自动开启、风扇 0；拔线后未再读取或改变设备状态。
