# GameNative 滤镜移植与验证记录

日期：2026-09-04。目标设备：AYN Odin 3，屏幕输出 1920 × 1080，GPU 为 Adreno 830。

本次已安装用户提供的 [GameNative 1.2.0 官方 APK](https://downloads.gamenative.app/releases/1.2.0/gamenative-v1.2.0.apk)，并将其屏幕效果算法接入 Odin 桌面的 GPU 截图预览。设置入口独立放在系统下拉面板，支持关联当前应用保存，也支持无游戏时独立调节截图。下文的 **Vulkan、OpenGL 表示保留的两套算法家族**，不是面板要求用户选择的运行模式；其中 Vulkan 公式经过适配，在 GLES 3 上执行，并非原生 Vulkan 后端，也不表示已经 Hook 到游戏进程。

## 2026-09-06：运行状态修复

Issue #4 已实现保存意愿与实时运行状态分离。当前组合、权限、目标切换和覆盖层绘制均纳入状态判定；即使遮罩完成绘制，也明确显示游戏效果未确认。实机允许/屏蔽浮层的对照证明不能仅凭 `addView` 或 `onDraw` 宣称生效。见 [本轮验收](completion-validation.md)。完整实时游戏帧接入的边界与下文一致。

## 已交付的能力

| 设置 | OpenGL 家族 | Vulkan 家族 |
| --- | --- | --- |
| 独立效果开关 | Toon、FXAA、Vivid、CRT、NTSC | Toon、FXAA、Vivid、CRT、NTSC |
| 缩放 | 无、最近邻、线性、裁切填满、拉伸、FSR、FSR 保持比例 | 左列全部，以及 DLS、Natural |
| 亮度、对比度 | −100%～100% | 同左 |
| Gamma | 0.5～2.5，默认 1.0 | 同左 |
| FSR 锐度 | 1～5，默认 3 | 同左 |

上表描述底层保留并验证的算法能力。两套算法分别保留；当前控制面板由程序选择已有兼容路径，不显示 OpenGL、Vulkan 或软件模式选择器。OpenGL 的 DLS、Natural 不在上游支持范围内。这里的“全部效果”指 GameNative 当前屏幕效果菜单，不包含其独立 cnc-ddraw 资源库中所有 GLSL 文件，也不将旧版本遗留的隐藏 effect ID 当成菜单选项。

入口已从桌面应用操作菜单移到系统下拉面板的 **滤镜开关**。**短按切换当前应用滤镜，长按打开调节面板**；长按由系统 `QS_TILE_PREFERENCES` 入口进入 `ShaderControlActivity`。面板自动关联呼出下拉栏前的目标应用，不要求用户手动选择应用，也不把历史目标自动当成当前游戏。

调节面板以截图作为全屏背景，右侧栏包含游戏开关、效果选择、默认折叠的更多参数，以及“更换截图”和“显示原图”按钮。可以收起右侧栏查看完整画面，按 X 切换原图与效果，按 B 返回。修改参数会立即更新背景的 GPU 预览；当前亮度、对比度滑杆步长为 1%，Gamma 步长为 0.05。

有目标应用时，面板读取并保存该应用的配置；连续修改串行写入，关闭前等待保存完成。**未运行游戏也可以导入截图、选择效果和调整全部预览参数**，仅游戏滤镜开关不可用；这些独立预览设置单独保存在本机，不写入其他应用的配置。预览的变化针对已导入的静态截图，不表示已经处理正在运行的游戏帧。

## 算法、分辨率与适配

扫描线和屏幕纹理依据物理输出尺寸计算；此次对照输出固定为 1920 × 1080，不使用 Compose 的 dp 或缩略图大小定义扫描线密度。Vulkan 菜单 CRT 保留原扫描线和竖栅格强度，改用 `gl_FragCoord.xy` 计算相位，避免适配图片后的 UV 让两像素周期随截图宽高比变化；这是一项有意适配，不保证与上游任意 viewport 逐像素相同。截图的实际输入尺寸另行保留，用于缩放比例和 FSR 重建。已经放大到屏幕分辨率的截图无法提供原游戏低分辨率帧中缺失的额外细节。

OpenGL 组合按缩放、色彩、Toon、FXAA、Vivid、CRT、NTSC 的顺序运行；OpenGL FSR 包含 EASU 与 RCAS 两个阶段。Vulkan 家族保持上游单个着色器中的组合顺序和重新采样源纹理的行为，其 FSR 使用上游 EASU 加轻量锐化实现。两家的同名 FXAA、CRT、Vivid、Toon 等算法存在实质差异，不能互换命名。

OpenGL CRT 保留 RGB 采样偏移；原固定 1024 相位按 1920 × 1080 参考屏幕换算，使参考尺寸下保持原波形、其他输出尺寸下跟随物理像素。OpenGL NTSC 预览使用 60 Hz 参考时间换算相位，便于固定相位比较；GameNative 原实现按实际渲染帧推进。GLES 语法、输出格式及其他适配详见 [第三方来源与适配说明](../THIRD_PARTY_NOTICES.md)。这些改动意味着“移植公式”不等于“任意 GameNative 游戏实时输出逐像素完全一致”。

官方 APK 的 SHA-256 为 `427b760b4bd99201a13ad45c18296db83186e471631b4612932cfc3f07f414c1`。本次静态对照确认：APK 中 9 个 OpenGL shader 字符串与所用源码完全一致；APK 与仓库预编译 Vulkan 库的已分配 ELF 节及内嵌顶点、片元 SPIR-V 一致。此证据不涵盖 Java/Kotlin 调度字节码的穷尽比较，也没有重新编译上游 GLSL 来证明编译源码来源。对应的 `apk120-baseline-verification.json` 和验证脚本已归档到下文的 `shader-comparison` 目录。

## 实时游戏范围与原生接入

当前实时路径仍是既有 Vulkan CRT 遮罩的兼容模式。它按屏幕像素使用 `0.86 + 0.14 × sin(y × π)` 与 `0.94 + 0.06 × sin(x × π)`，通过黑色透明遮罩完成亮度乘法。窗口透明度显式设为 0.8，shader 使用同一个常量补偿；不再采用旧实现的三像素周期和加深扫描线参数。

只有启用 Vulkan CRT、缩放为“无”、色彩为默认且其余效果关闭的组合可走该兼容路径。需要采样游戏像素的选项当前仅保存供预览；引擎会移除已有遮罩并提示需要原生接入，不会继续用 CRT 冒充所选效果。关闭 CRT、离开目标应用或切到桌面时也会移除遮罩并释放资源。

当前策略选择已有的 CRT 兼容路径，并显示是否有目标、是否具备悬浮窗权限。程序未通过包名或 APK 内的图形库猜测当前游戏 API；不能将此描述为已经识别任意进程使用的 OpenGL、Vulkan 或 CPU 渲染方式。

本次没有实现跨进程 Vulkan/OpenGL Hook，没有增加 MediaProjection 录屏管线，没有进行 root、解锁或刷机。完整实时效果仍需接入目标应用的渲染流程。

Android 官方支持通过外部图形 layer 接入符合条件的应用。GLES layer 要求 Android 10+、GLES 2.0+；外部 layer 的目标需满足可调试、在目标 SDK 30+ 的 manifest 中主动声明 `com.android.graphics.injectLayers.enable=true`，或运行于提供 root 的 userdebug 系统等条件。普通桌面 APK 不能替另一个发布版应用授予该声明。[Android GLES layers](https://developer.android.com/ndk/guides/rootless-debug-gles)

Vulkan 外部 layer 也有目标应用资格限制；Android 9+ 可从应用外部位置加载，Android 10+ 可从独立 layer APK 加载。应用自身也可以打包并在创建 Vulkan 实例时启用 layer，因此可修改源码的目标存在无需 root 的接入路径。[Android Vulkan validation layers](https://developer.android.com/ndk/guides/graphics/validation-layer)

本次读取的已安装 SkyEmu manifest 未声明 `debuggable` 或上述 `injectLayers` 标记；不能据此假定能用官方外部 layer 对该发布版直接注入。后续仍需核实目标进程 ABI、系统构建类型及实际图形加载器。GameNative 的自定义驱动路径也可能绕开系统 Vulkan loader，需要在真实调用路径上接入。

获得 root 不代表自动具备通用 Hook 能力，也不等同于 userdebug 系统；Android 的 SELinux 访问控制同样约束 root 进程。[Android SELinux](https://source.android.com/docs/security/features/selinux) 原生接入还需处理上下文、纹理来源、交换链、同步、旋转与颜色空间。优先在可控应用内复用 GPU 纹理是当前设计方向；其性能收益仍需实际测量，不能由“已经 root”或“使用 Vulkan”推出。

## 息屏挂机

系统下拉面板的 **息屏挂机** 磁贴独立控制黑色 OLED 浮层，副标题显示“挂机中”或“未开启”。点击后收起系统面板并启停遮罩；缺少悬浮窗权限时进入授权页面。遮罩保留原游戏窗口焦点、接收触摸以实现双击屏幕退出，暗色状态文字周期漂移。停止或创建遮罩失败时释放覆盖层与 WakeLock。

该功能把画面遮黑并保持显示屏开启，不调用 Android 物理熄屏，也不承诺游戏全速运行。上述说明描述实现行为；磁贴在 OEM 面板中的最终顺序、OEM 原组件状态及 AFK 真机交互验证需分别记录，不能由此前的 shader 静态图测试推出。

## 设备验证记录

按用户要求，本次从 SkyEmu 3.2（`com.sky.SkyEmu`）启动 GBA《塞尔达传说：缩小帽》（The Legend of Zelda: The Minish Cap），取得无 shader 的基准截图。后续效果比较使用同一张图，未将游戏帧差异当成 shader 差异。基准使用 SkyEmu 的像素化采样，临时关闭颜色校正与残影，并停用桌面遮罩；测试后已核对恢复颜色校正强度 1.00 与残影开启，设备回到 Odin 桌面。

最终设备 GPU 批量验证共 31 项，包括原图、两家缩放模式、各单项效果、色彩参数及组合效果；报告为 **31/31 rendered**，输出均为 **1920 × 1080**，实际渲染器为 **Adreno (TM) 830**，输出色彩空间为 **sRGB IEC61966-2.1**。无效果路径的 **original_mismatches = 0**；本机又用 Pillow 比较 `source-srgb.png` 与 `original.png`，确认逐像素相等。

加强原图校验时发现，ADB 基准截图携带 Display P3 ICC 配置，而 GPU 导出使用 sRGB。输入解码现已显式指定 sRGB，使上传 GPU 前的像素完成色彩空间转换，上述最终结果在修正后重新取得。无效果比较以同一 sRGB 参考为准，不能直接把原始 P3 文件的 RGB 数值当成 sRGB 数值比较。原始 P3 截图仍保留为 `gba-original.png`。

这些结果验证了 GPU 出图、静态图像对照与无效果路径的像素保持，未覆盖所有参数组合、任意游戏、实时 Hook、帧率或帧时间，也没有证明与 GameNative 游戏实时画面逐像素一致。

最终机器可读报告 `results.json`、`pixel-verification.json`、基准和导出效果图位于：

```text
/Users/suntao/.codex/visualizations/2026/09/04/01a06a37-307e-7132-b2a0-87909c0d7edf/shader-comparison/
```

其中 `gba-original.png` 为原始 P3 基准，`source-srgb.png` 为转换后的参考，`original.png` 为无效果输出，`vulkan_*.png`、`opengl_*.png` 用于静态效果比较。文中临时审计目录是此次本机记录，不是项目构建依赖。

## 数据迁移与回退

Room 数据库版本从 2 升到 3，仅给 `app_shader_configs` 增加非空 `effectsJson` 列，默认空字符串。原分组、应用映射、开关与旧参数列保留；旧行的空 JSON 解析为原 CRT 默认组合。还提供 1 → 2 的建表迁移，并移除了破坏性迁移兜底。升级前后实际数据库对照确认 3 个分组、24 个应用映射逐行一致，5 条旧滤镜配置除新增列外保持一致。

本次旧 Odin 桌面 APK 与升级前数据库备份已归档到：

```text
/Users/suntao/.codex/visualizations/2026/09/04/01a06a37-307e-7132-b2a0-87909c0d7edf/shader-comparison/rollback/
```

已核实存在 `odin-desktop-before.apk` 与 `odin-database-before.tar`；后者包含 `odin_desktop.db`、`odin_desktop.db-wal`、`odin_desktop.db-shm`。

执行回退前，先核实归档中 APK、数据库及其版本信息，并额外备份届时的当前数据库。旧 APK 使用 schema 2，不能直接搭配已升级的 schema 3 数据库；应将旧 APK 与经过核验的升级前 schema 2 快照配套恢复。数据库备份必须使用一致快照，涉及 WAL 时同时考虑其未检查点写入。若需保留升级后新修改的分组或设置，应先导出并明确迁移这些数据，再恢复旧版本。恢复后核对分组、应用映射和旧滤镜开关。

本回退说明不使用卸载或清除应用数据作为恢复手段，也不修改模拟器的 ROM、BIOS、存档和用户配置。备份存在不等于已经执行回退；若实际恢复，应另行记录对应验证结果。
