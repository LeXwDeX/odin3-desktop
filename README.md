# 🎮 Odin 3 Desktop (`odin3-desktop`)

<div align="center">

**专为 AYN Odin 3 安卓掌机深度打造的旗舰级桌面启动台与系统增强套件**

[![Release](https://img.shields.io/badge/Release-v0.1.0-cyan?style=for-the-badge&logo=android)](https://github.com/LeXwDeX/odin3-desktop/releases)
[![Platform](https://img.shields.io/badge/Platform-AYN%20Odin%203%20(Android%2015)-black?style=for-the-badge&logo=qualcomm)](https://github.com/LeXwDeX/odin3-desktop)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20%7C%20OLED%20Black-blue?style=for-the-badge&logo=jetpackcompose)](https://github.com/LeXwDeX/odin3-desktop)
[![Shader](https://img.shields.io/badge/Shader-AGSL%20VideoShader-orange?style=for-the-badge&logo=opengl)](https://github.com/LeXwDeX/odin3-desktop)

*纯粹掌机美学 • 100% 全实体手柄盲操 • 嵌入式 VideoShader 渲染管线 • 智能温控风扇调度*

</div>

---

## 📸 掌机视觉宣传画册 (Showcase)

### 1. 极致纯黑 OLED 掌机桌面 (Console Home)
采用全对称中轴线视网膜大卡片滑带设计，背景深度适配 Odin 3 AMOLED 屏幕纯黑低功耗特性（`#000000`）。支持毫秒级手柄摇杆光标跟随与触控操作 100% 绝对同频同步。

![掌机主界面](docs/screenshots/01_launcher_home.png)

---

### 2. 滤镜开关与截图调节（当前测试入口）

**开发顺序（2026-09-04 确认）**：先完善滤镜、截图调参和息屏挂机，最后统一集成到桌面控制台。现阶段保留下拉入口用于测试，迁移时复用已有配置与功能实现。最终入口以 [需求约定](REQUIREMENTS.md#开发顺序与最终入口) 为准。

系统下拉面板中的 **滤镜开关** 短按切换当前应用的滤镜，长按打开独立调节面板。面板以截图作为全屏背景，右侧调节栏可以收起；修改效果和参数时，背景直接显示 GPU 处理结果。桌面应用操作菜单中不再放滤镜设置。

* **截图对照**：点击“更换截图”导入图片；按住 X 显示原图、按住 Y 隐藏参数，松开恢复。触摸也可切换原图和收起参数栏。未运行游戏时也能调节截图，预览设置单独保存在本机。
* **手柄调参**：滑条先按 A 进入调节，左右改变数值，再按 A 确认；B 取消并恢复进入时的值。返回操作逐层关闭选择弹层、结束调节、收起高级参数和参数栏，最后退出页面。
* **当前应用**：有目标应用时自动加载并保存其设置；没有目标时仅禁用游戏滤镜开关。用户无需选择应用或图形 API。
* **实时兼容**：现有系统覆盖层仅支持不读取游戏像素的 Vulkan CRT 扫描线。
* **接入范围**：其他效果当前用于 GPU 截图预览。程序选择已有兼容路径，尚未实现跨进程 Hook，也没有通用的游戏图形 API 识别。
* **数据升级**：Room 1 → 2 → 3 显式迁移，保留已有分组、应用映射与滤镜设置。

算法适配与第三方许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，验证与接入限制见 [docs/shader-port.md](docs/shader-port.md)。

磁贴排列、截图调参验收、原版游戏助手停用与恢复方式见 [下拉控制交付记录](docs/quick-controls.md)。

下拉面板另有 **息屏挂机**：开启黑色 OLED 遮罩，保留游戏窗口焦点，并以暗色漂移文字显示状态；双击屏幕退出。该功能通过黑色浮层遮住画面，Android 显示屏仍保持开启，不是物理熄屏，也不保证游戏以固定帧率运行。

---

### 3. 应用专属操作控制台 (App Actions)
针对掌机交互定制的高效操作菜单：长按 `Y` 键即刻唤出。支持无损归类到其他 Tab 分组、一键直达系统应用属性详情（免权限安全卸载与停用）或从当前分组移除。

![应用专属操作面板](docs/screenshots/02_app_actions.png)

---

### 4. 系统级深度控制 — 摇杆 RGB LED 氛围灯 (LED Customization)
提供 6 种摇杆灯颜色预设。颜色与开关通过受限硬件后端写入并读回确认，后端不可用时提示失败。新后端的启用条件见 [硬件控制说明](docs/quick-controls.md#受限硬件后端)。

![摇杆 RGB LED 灯光控制](docs/screenshots/04_config_led.png)

---

### 5. 屏幕方向与握持倒置策略 (Orientation Rules)
支持「传感器自适应双向横屏」与「默认握持固定横屏」两种模式。下图为早期界面示意，当前选项以应用为准。

![屏幕旋转与换向策略](docs/screenshots/05_config_orientation.png)

---

### 6. 模块化 Tab 自定义分组与批量管理 (Tab Management)
告别乱糟糟的传统手机式应用抽屉。为掌机用户量身打造「系统全集」、「游戏与模拟器」、「影音媒体」、「系统工具」及自由创建的新分组；长按 `X` 键支持全量应用快速检索与批量勾选归类。

![Tab 分组与分类编辑](docs/screenshots/06_config_tabs.png)

---

## Dashboard

首页最左侧的 Dashboard 异步显示内置存储、每个已挂载外部卷、内存、CPU/GPU 真实温度和 Wi-Fi 状态。CPU/GPU 保留温度数字与固定 0–105°C 温度条，不采样或显示使用率；未知温度显示“— °C”和空轨道。统计卡片只读，不接受点击或手柄焦点。

常用操作保留四项：文件管理、系统设置、Odin 设置、滤镜调整。已取消内存清理与磁盘清理。宽屏下四个按钮等宽，下面保留五项硬件 Dock；存储区域与 CPU/GPU 组合共用左右边界，RAM 与 Wi-Fi 共用另一列边界。滤镜调整可直接使用已有截图，不必启动游戏。采样口径见 [Dashboard 说明](docs/dashboard.md)。

Dock 按 A 循环性能“默认／性能／高性能”、风扇“关闭／智能／高性能”，或切换摇杆灯、充电限制、飞行模式。风扇聚焦时按 X 切换充电模式；手动选择风扇档位会停用该策略。充电限制同时切换 80% 上限与 5V 档，界面分别显示两项读回状态，电流取决于电源与充电协商，不表示实测 3A。

本轮布局、Dock 后端和滤镜按键改动尚待实机验收；下方文档中的既有验证均标注为上一版记录，不能用来证明当前版已通过。

## 🕹️ 全硬件实体按键交互指南 (Gamepad Controls)

| 按键 | 触发场景 | 功能说明 |
| :--- | :--- | :--- |
| **D-Pad / 左摇杆 (左右)** | 桌面大卡片 | 在当前分类的应用卡片之间水平循环导航，带平滑弹性滚动动效 |
| **D-Pad / 左摇杆 (上下)** | 桌面各区域 | 在顶部 Tab、中部四项常用操作或应用卡片、底部 Dock 之间移动；统计卡片不聚焦 |
| **L1 / R1** | 桌面主层 | 向前 / 向后切换 Dashboard 与应用分类页面；弹窗内不切换页面 |
| **A 键 (确认)** | 应用 / 菜单项 | 点击启动选中应用；在设置/操作弹窗中确认选定项 |
| **B 键 (返回)** | 弹窗 / 排序模式 | 退出当前操作弹窗、取消应用排序并保存当前状态 |
| **Y 键 (短按)** | 应用卡片区 | 进入/退出当前分类的图标排序模式 |
| **X 键 (长按)** | 应用卡片区 | 呼出当前 Tab 的**批量增删分类应用**弹窗，支持全拼/首字母即时搜索与勾选 |
| **Y 键 (长按)** | 应用卡片区 | 呼出**应用专属操作菜单**（Tab 迁移、系统属性详情、移除图标） |
| **滤镜开关短按** | 系统下拉栏 | 切换当前应用滤镜；实际生效范围取决于已有兼容路径 |
| **滤镜开关长按** | 系统下拉栏 | 打开全屏截图调节面板；可更换截图；按住 X 看原图、按住 Y 隐藏参数，松开恢复 |
| **A / 左右 / A** | 滤镜滑条 | 进入调节、改变数值、确认；B 取消本次调节并恢复进入时的值 |
| **A / B** | 滤镜选择弹层 | A 选择当前项，B 关闭并保留原选择 |
| **B** | 滤镜调整 | 逐层取消调节、收起高级参数和参数栏，最后返回桌面或游戏 |
| **L1 / R1** | 滤镜主层 | 参数栏可见且高级参数未展开时切换预设；滑条调节和选择弹层内不切换 |
| **A** | 硬件 Dock | 循环性能或风扇三档；其余项目切换开关 |
| **X** | Dock 风扇卡片 | 切换充电模式，触摸卡片副行也可操作 |
| **息屏挂机** | 系统下拉栏 | 开启或停止黑色遮罩；遮罩显示时可双击屏幕退出 |

---

## ⚡ 核心技术架构 (Architecture Highlights)

```
com.odin.desktop
├── dashboard/              # 异步只读统计、外部卷与四项常用操作
├── data/
│   ├── db/                 # Room Database (Tab 分类、应用排序、Shader 配置)
│   ├── entity/             # 数据实体定义与 DAO
│   └── repository/         # 硬件服务与数据仓库 (App, Hardware, Shader)
├── shader/
│   ├── control/            # 长按磁贴打开的全屏截图调节面板
│   ├── runtime/            # 已有兼容路径选择与状态说明
│   ├── engine/             # VideoShaderEngine 掌机生命周期全局调度中心
│   ├── pipeline/           # IVideoShaderPipeline 接口体系
│   │   ├── AgslVideoShaderPipeline.kt  # Android 15 AGSL 硬件着色器 (GameNative 算法)
│   │   └── slang/          # RetroArch .slang / SPIR-V 多通道着色器未来扩展桩
│   ├── overlay/            # 滤镜开关磁贴与 CRT 兼容覆盖层
│   └── model/              # AppShaderConfigEntity 配置实体
├── service/
│   ├── fan/                # AppMonitorAccessibilityService 前台应用变化嗅探
│   └── afk/                # 息屏挂机磁贴与黑色 OLED 遮罩
└── ui/
    ├── components/         # 掌机级 Compose 组件库 (ConsoleModalDialog, 卡片滑带)
    ├── navigation/         # FocusZone 掌机绝对手柄焦点管理核心
    └── viewmodel/          # LauncherViewModel 单一可信数据源与状态机
```

* **Android 15 与 Compose 界面**：本项目面向 Odin 3；具体帧率和功耗需单独实机测量。
* **硬件控制边界**：新受限 ADB 桥只监听本机 `127.0.0.1:18889`，验证请求身份并限制可写项目；设备重启后需连接电脑重新启用，不等同于 root。
* **覆盖层输入处理**：CRT 兼容层不接管焦点或触摸；挂机遮罩保留游戏焦点、接收触摸以实现双击退出。

---

## 📦 安装与编译构建 (Build & Install)

### 环境要求
* JDK 17+
* Android SDK (API 35 / Build-Tools 35.0.0)
* AYN Odin 3 掌机设备 (已开启 USB 调试)

### 本地编译与安装
```bash
# 1. 克隆本仓库
git clone git@github.com:LeXwDeX/odin3-desktop.git
cd odin3-desktop

# 2. 编译生成 Debug APK
./gradlew assembleDebug

# 3. 安装到已连接的 Odin 3 掌机
adb install -r -d app/build/outputs/apk/debug/app-debug.apk

# 4. 启动桌面
adb shell "am start -n com.odin.desktop/.ui.MainActivity"
```

---

## 📜 许可与致谢 (Credits & License)

* **GameNative & Winlator**：感谢开源社区对于 CRT 扫描线着色器与复古渲染管线的卓越算法贡献。
* **AYN Odin 社区**：专为追求纯粹安卓掌机体验的硬核玩家打造。

*License: [Apache-2.0](LICENSE)*
