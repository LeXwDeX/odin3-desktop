# 2026-09-06 代码审计与优化验收

以 `ae9fb74` 为起点，检查本项目 Android 应用、共享硬件事务、Shader 资源、Manifest、数据层及构建回归。重点沿启动、硬件操作、后台生命周期、分类编辑和图片输入的调用链核对源码；对生产代码、资源和测试逐路径检查图谱覆盖，并运行全应用 Lint。Tier 2 图谱在本轮自动更新，覆盖核验代次包括 `2026-09-06T00:26:59Z`；Shader 精度声明的解析缺口已直接读取。图谱与静态检查不能证明不存在其他缺陷，本轮也不是对原厂 ROM 或所有第三方应用的穷尽验证。

## 修复结果

| 触发条件 | 修复后的行为 |
| --- | --- |
| 自动策略遇到短暂 >60°C 温升 | 使用单调时间累计连续温升，16 秒后才进入智能；75°C 立即响应 |
| 自动温控触发后温度下降 | <=55°C 持续 24 秒才恢复安静，避免在阈值附近反复切换 |
| 事件风暴、采样中断、传感器失败 | 事件数量不能加速温控；采样间隔过长重置连续窗口；读失败不能当作低温 |
| 手动选择风扇关闭 | 关闭自动调度并释放策略所有权，本应用不再因温度尖峰覆盖选择；原厂保护仍可独立工作 |
| 电池充满、分离供电时状态为 FULL / DISCHARGING | 根据插电字段判断外接电源，不再误当成拔线而无法自动停转 |
| 先手动 OFF 后重新开启自动，停扇状态未被策略接管 | 满足安静条件时即使无需重复写入也接管 OFF，未知前台或断电时可恢复散热；新增回归先复现失败再验证修复 |
| 自动调度关闭后仍显示常驻服务 | 无待恢复操作时停止风扇前台服务；手动硬件控制不需要该服务 |
| 每分钟强制尝试重绑无障碍 | 删除重绑轮询和不再使用的 WRITE_SECURE_SETTINGS 权限；由用户在系统中启用监控 |
| 仪表盘、风扇和设置页重复扫描温度传感器 | 共用 1 秒采样缓存，传感器路径每 60 秒才重新发现 |
| 设置弹窗留在后台，或打开其他设置 section | 温度轮询仅在自动风扇 section 可见且 Activity STARTED 时运行 |
| 后台策略正在硬件事务中，主界面查询服务是否需要恢复 | 使用可见的布尔状态提示，主线程不等待硬件事务锁 |
| AMD FSR 选项没有对应用户所需效果 | 删除选项、EASU/RCAS 资产和分支；旧 FSR 配置回退无缩放，其余设置和 DLS 锐度保留 |
| 多个 Launcher Activity 属于同一个应用包 | 按包去重；图标或名称在扫描时不可读不会丢掉整份应用列表 |
| 分类重命名使用旧的整行数据 | 只更新名称字段，保留最新默认项、分类属性和顺序 |
| 过期拖拽排序、并发添加或目标分类被删除 | 事务内读取最新映射；不复活移除项，不丢并发新增项；跨分类移动在同一事务中完成。自动“全部应用”保留首次排序能力 |
| 预览入口先解码大图再检查尺寸，导入写入中失败 | 两个入口共用有界解码与串行原子文件写入；先读尺寸，拒绝超过 3200 万像素的图片；失败保留原截图 |
| 自动风扇新说明在原设置区域被裁切 | 仅调整该 section 的行高与间距，并允许长内容滚动；顶部和 Dock 布局不变 |

## 架构结论

目前保留一个 `:app` 模块合理，已有包边界能隔离界面、硬件事务、策略和数据持久化。纯 Java 的 `FanControlCoordinator` / `ThermalGate` 可以独立回归；`HardwareController` 统一操作、完成通知及按需同步 Android 服务；服务只负责事件合并和周期调度。原厂 Binder 的命令白名单、读回和回滚留在 `HardwareOperations`，普通 UI 不直接拼装硬件命令。

分类仓库承担事务边界；图片读取和原子写入合并到 `ShaderSourceImage`，消除两个预览入口的行为差异。截图 GPU 渲染与没有游戏帧输入的实时兼容遮罩继续分开，删除 FSR 不会把其他效果冒充为全设备实时滤镜。`LauncherViewModel` 仍较大，主要承载现有手柄导航状态；已有硬件控制、遥测和数据仓库分离，本轮不为拆文件而重写交互状态机。

审计也核对了 AFK 的显式启动/停止、12 小时有界 wake lock、30 秒漂移和销毁清理；顶部与 Dashboard 的可见性取消；应用内公开入口和 release/debug 探针隔离；数据库迁移没有清空用户数据的 fallback。未新增动态代码加载、外部命令接口、Root 或电脑运行时依赖。

## 本地验证

使用项目内 JDK/SDK 与缓存：

```sh
tools/android ./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug --offline
tools/android python3 tools/fan-policy/test.py
tools/android python3 tools/architecture-regression.py
tools/android python3 tools/cooling-ui-regression.py
tools/android python3 tools/home-back-regression.py
tools/android python3 tools/fan-state-completion-regression.py
tools/android python3 tools/hardware-bridge/build.py --sdk .android-local/sdk --java-home .android-local/jdk/Contents/Home
```

风扇协调器通过 221 项检查，共享温度读取覆盖缓存、单位、缺失传感器、无效值和恢复。UI 队列通过 9 组方法级回归，包括 1000 次同类操作和 3000 次混合按键；Home/Back 各 100 次替身回放没有重复扫描或 Activity 返回分派。资源校验覆盖 361 个 key 及格式参数；实际迁移 SQL、DAO 保护、Shader 预设和输入要求均通过。

Debug / Release 构建通过，20 项 JUnit 测试零失败；共享硬件事务与 OEM 命令校验 142 项通过。Lint 为 0 错误、90 警告，主要是依赖升级提示、未使用资源、私有固件 API 和既有 UI 约定；没有用整库 baseline 隐藏问题。固定横屏是掌机产品约定，UNKNOWN_SSID 是内联字符串；AFK 通知点击仅停止服务，不通过通知间接拉起 Activity。仍有图标单色资源和触摸无障碍适配等改进项，不将警告数量写成“无 bug”结论。

Robolectric 覆盖 Android 12L / 15 的语言行为、实际 Room 仓库、FSR 旧 JSON 迁移、Shader 状态、服务启停和原生图片解码。图片测试修改 PNG 头声明 1 亿像素，验证在分配像素前拒绝且原截图保留；有效图片往返和损坏图片导入也通过。替身测试、Robolectric 和编译结果均不能代替原厂硬件实测。

## 系统“运行中应用”与功耗

Android 13 起会把前台服务列入“运行中的应用”。自动风扇或 AFK 正在提供后台功能时仍应出现；关闭这些功能后不再为手动控制常驻。这是系统的服务透明提示，不能作为高耗电的直接证据。参考 [Android 前台服务与 Active apps](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping)。

本轮主要减少不需要的后台工作，没有通过隐藏通知、禁用系统保护或伪装服务状态来消除提示。CPU 短窗口采样和实际电池续航不同；设备接电时不能据此计算续航提升百分比。

## 实机记录

使用已确认签名覆盖安装，保留分类、设置、游戏及存档。设备为 Odin 3、Android 15。原始诊断与采样保存在忽略目录 `.android-local/device-analysis/`，截图源和导出图不进入 Git。

手动关闭的实测：自动偏好为 false，`fan_mode=0`、驱动 `state=0 / duty=0`，`dumpsys activity services ...FanWatchdogService` 为 `(nothing)`。原厂控制继续通过 `PServerBinder`，没有启动旧开发桥。中文自动风扇设置页实测完整显示两组温控条件，截图为 `auto-fan-final.png`。

同一 30 秒 `/proc/<pid>/stat` 采样方法：优化前单次约 1.19% 单核 CPU，最终仪表盘前台约 1.53%，设置页保留但退到后台约 0.23%。采样均保持同一 PID；没有严格控制前后负载，不能声称 CPU 或电量降低了某个百分比。明确验证的优化是手动模式无风扇前台服务、后台 UI 采样取消和温度路径扫描减少。


GPU 验证在 Adreno 830 上执行 27 个剩余组合，全部完成真实编译和渲染，JSON 中没有 FSR 用例；1920×1080 原画对比为 0 个像素差异。结果为 `gpu-without-fsr.json`。这证明截图管线仍可工作，不代表对任意第三方游戏的实时帧处理已经实现。


实时 Shader 状态探针在最终设备上报告 `Enable app monitoring before this test`，因此未执行运行状态/权限撤销矩阵，不能列为本轮通过项。报告确认测试配置已恢复、浮层权限为原值 allow。未代替用户开启无障碍监控；隔离测试包随后清理。此限制与已通过的 27 组 GPU 截图渲染验证分别记录。


最终 APK 的自动风扇往返实测通过：覆盖安装后等温度稳定进入 `fan=0 / state=0 / duty=0`；退到系统设置（应用监控关闭，前台未知）约 1.37 秒进入 `fan=4 / state=1 / duty=12500`；按 Home 返回约 0.74 秒回到 `fan=0 / state=0 / duty=0`。首次等候停扇约 54.75 秒，包含安装后的温度稳定过程，不当作固定策略延迟。报告为 `final-fan-roundtrip.json`。最终保留自动偏好 true、旧自启偏好 false、默认横屏和原校准高清 FXAA 设置，HOME 解析与实际前台均为本应用。自动开启时前台服务仍按 Android 规则展示运行提示。
