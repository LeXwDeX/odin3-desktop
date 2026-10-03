# 控制台界面架构

本文记录当前启动器界面的组合边界、矢量背景绘制方式、省电暂停条件和验收入口。业务状态、设备输入与持久化仍由既有 ViewModel、MainActivity 和 Room 负责。

## 组合边界

`LauncherScreen` 保持 MainActivity 使用的公开入口。它负责屏幕装配、方向副作用和背景层，不接管业务状态或按键分发。

```mermaid
flowchart TD
    A[MainActivity] --> B[LauncherScreen]
    B --> C[LauncherBackdrop]
    B --> D[LauncherContentRoute]
    B --> E[LauncherHeaderRoute]
    B --> F[LauncherDockRoute]
    B --> G[LauncherDialogRoutes]
    H[LauncherViewModel / StateFlow] --> D
    H --> E
    H --> F
    H --> G
    I[MainActivity.dispatchKeyEvent] --> J[GamepadKeyHandler]
    J --> H
    K[Room] <--> H
```

各路由只收集自己显示所需的状态。`collectAsVisibleState` 使用 `repeatOnLifecycle(STARTED)`。Dashboard 状态不会由 Dock 持有；硬件读数不会由应用网格持有。关闭的弹窗只收集各自的打开状态，其余数据在弹窗打开后才订阅。

`AppIconCollection` 保持应用条带、全屏网格、`[+]` 入口、焦点滚动和拖拽排序的现有职责。Y 键、D-pad、A/B、肩键仍由 `MainActivity.dispatchKeyEvent` 转交 `GamepadKeyHandler` 与 ViewModel 处理。此次 UI 拆分不改变 Room 数据、导航状态或 MainActivity 的入口 API。

## 动态背景与暂停边界

当前 v0.1.25 候选背景用 Canvas 绘制深蓝至靛色的淡纵向渐变和静态径向柔光。动态部分由两条缓存的 Path 构成：一条宽幅主面和一条较淡的伴带。主面中央上下边界的间隙约为 0.17H，随后向两侧展开。主面以内部渐变表现面光影；没有第三条折面，也没有描线。Path 和 Brush 在 `drawWithCache` 中按绘制区域尺寸和调色板创建；每帧只读取相位并绘制，不重置或重建几何。

两条 Path 在同一绘制变换中运动，因此全幅同相。运动使用 24 秒正弦周期，更新间隔为 50 ms，上限为 20 次/秒。令 `s = sin(2πphase)`，平移量为 `x = 0.024W·s`、`y = 0.045H·s`；缩放量为 `scaleX = 1 + 0.009s`、`scaleY = 1 + 0.008s`。在转向点速度为零、加速度非零，整周期连续。相位只在绘制阶段读取，因此更新不会让界面组合树重新执行。聚焦边框仍由共享组件绘制三层浅蓝白半透明矢量描边，形成轻微软 halo；没有使用真实模糊滤镜。以上是实现参数，不代表帧率、功耗或续航实测结果。

动画必须同时满足以下条件：界面请求动态效果、Activity 处于 `RESUMED`、窗口有焦点、绘制 View 已附着、系统省电模式关闭、系统动画倍率大于 0。配置、应用操作、批量管理或排序弹窗打开时，以及应用图标重排时，启动器会关闭背景运动。恢复后保留原相位，不累计隐藏期间的时间。

背景是内容后方的装饰层。它不接收按键或指针输入，也不占用导航焦点。页面切换、弹窗、焦点和拖动行为仍由前景组件负责。

## 回归与设备验收

本地回归命令：

```sh
tools/android ./gradlew :app:testDebugUnitTest
tools/android python3 tools/architecture-regression.py
tools/android python3 tools/home-back-regression.py
```

`BackgroundMotionTest` 检查每个暂停门、暂停后相位保持、缓入缓出往复的端点与中心，以及时钟按实际帧时间推进和循环；当前背景测试 6/6 通过。单元测试与架构脚本不能证明实机绘制、功耗、续航或触控拖拽体验。

设备验收应在确认 ADB 目标后进行，并覆盖：新配色与标题/焦点的可读性；Dashboard、应用主页、全部应用和设置弹窗；D-pad 导航、触控滚动、图标拖拽和排序；锁屏、失焦、省电模式与系统动画倍率关闭时背景停止；恢复后动画从原位置继续。验收时记录设备型号、固件、分辨率、字体倍率及观察结果。

2026-10-03 的 v0.1.22 旧界面曾在 AYN Odin3 / Android 15 上安装并取得短时 HWUI 窗口帧数据。v0.1.23 Debug 预览曾因层次不合格被否定。v0.1.24 已正式发布并完成保留数据安装，但用户认为曲线挤在中央、运动不同步，缓动感不足。v0.1.25 正式版尚未发布；同签名 Debug 预览 `0.1.25 / 1026` 已保留数据安装到 Odin3，用户对新背景的视觉反馈待回复。候选代码不代表 v0.1.24 正式 APK。安装与验证边界见 [v0.1.24 记录](releases.md#v0124-正式发布与安装)及 [v0.1.25 候选记录](releases.md#v0125-候选)。旧版短时窗口帧数据不证明背景独立开销、长时间功耗或续航。

### 参考与设计取舍

[Libretro 的 XMB 文档](https://docs.libretro.com/guides/xmb/)称其默认壁纸为 PlayStation 风格动态丝带；[公开 fragment shader](https://raw.githubusercontent.com/libretro/RetroArch/master/gfx/drivers/gl_shaders/pipeline_xmb_ribbon.glsl.frag.h)基于表面法线计算明暗。[Sony 的 PS5 设计说明](https://www.sony.com/en/SonyInfo/design/stories/PS5/)提到 Control Center 与 Game Help 使用克制、中性的 UI，以免干扰游戏。这些资料只作方向参考。

[Adam Oestergaard 在其项目页的自述](https://www.adamoe.com/work/sonyux)称，他曾与 Sony Design Center 和 global Central Design 团队合作两年，共同设计 Genome 等 UX 概念，并参与原始 PS4 UI/UX 的早期概念工作；他还称 ribbon 概念后来被其他团队采用并进入 PS4、Vaio 产品。该页面说明的是作者参与经历，不表示他独自设计了量产 PS4 界面，也没有公开 Sony 动画算法。

结合用户提供的 PS4 丝带参考，本项目的设计推断是：使用少量宽面、留出大块空白，以面内渐变表现光影，并让两条丝带同步运动。此设计不是对 Sony 动画算法的复刻，也不复制 Libretro 的 GPL shader 源码。

本轮 Lint 修复、保留原因和依赖升级建议见 [警告处理](lint-review.md)。
