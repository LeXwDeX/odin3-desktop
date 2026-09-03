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

### 2. 嵌入式 VideoShader 扫描线渲染管线 (CRT Pipeline)
算法精准移植自开源项目 **GameNative / Winlator**（`CRTEffect` 与 `window.frag`），基于 Android 15 硬件级 AGSL 运行时着色器构建。
* **每应用专属独立配置**：支持对特定模拟器或怀旧游戏开启专属扫描线，进入游戏自动唤醒，回到桌面自动休眠。
* **静态 0 额外功耗**：静态固定扫描线仅在首次合成时绘制一次，显卡与 CPU 0 额外负荷。
* **动态平滑扫频**：可自由切换平滑扫频动态动画、定制浓度深度、PVM 显像管 RGB 荧光格以及弧面微暗角。
* **系统通知栏一键切换**：内置 Quick Settings Tile 快捷磁贴，游戏内下滑通知栏随时 1 键开关。

![VideoShader 实时滤镜着色器](docs/screenshots/03_video_shader_config.png)

---

### 3. 应用专属操作控制台 (App Actions)
针对掌机交互定制的高效操作菜单：长按 `Y` 键即刻唤出。支持无损归类到其他 Tab 分组、一键直达系统应用属性详情（免权限安全卸载与停用）、呼出专属 VideoShader 配置或从当前分组移除。

![应用专属操作面板](docs/screenshots/02_app_actions.png)

---

### 4. 系统级深度控制 — 摇杆 RGB LED 氛围灯 (LED Customization)
直连掌机硬件控制节点，支持 6 款独家调校的极客背光色彩实时切换（青蓝、极客紫、战斗红、荧光绿、冰川白、暗夜灰），无需打开繁琐的系统设置。

![摇杆 RGB LED 灯光控制](docs/screenshots/04_config_led.png)

---

### 5. 屏幕方向与握持倒置策略 (Orientation Rules)
适配掌机接驳充电线、不同手柄外设及支架摆放姿态。支持「传感器自适应双向横屏」、「默认握持固定横屏」与「反向横屏（充电倒向握持）」3 种模式即时切换。

![屏幕旋转与换向策略](docs/screenshots/05_config_orientation.png)

---

### 6. 模块化 Tab 自定义分组与批量管理 (Tab Management)
告别乱糟糟的传统手机式应用抽屉。为掌机用户量身打造「系统全集」、「游戏与模拟器」、「影音媒体」、「系统工具」及自由创建的新分组；长按 `X` 键支持全量应用快速检索与批量勾选归类。

![Tab 分组与分类编辑](docs/screenshots/06_config_tabs.png)

---

## 🕹️ 全硬件实体按键交互指南 (Gamepad Controls)

| 按键 | 触发场景 | 功能说明 |
| :--- | :--- | :--- |
| **D-Pad / 左摇杆 (左右)** | 桌面大卡片 | 在当前分类的应用卡片之间水平循环导航，带平滑弹性滚动动效 |
| **D-Pad / 左摇杆 (上下)** | 桌面各区域 | 在 **顶部分类 Tab** ⟷ **中部应用卡片** ⟷ **底部快捷 Dock** 之间无缝流转焦点 |
| **L1 / R1** | 任意桌面状态 | 快速向前 / 向后切换应用分类 Tab 页面 |
| **A 键 (确认)** | 应用 / 菜单项 | 点击启动选中应用；在设置/操作弹窗中确认选定项 |
| **B 键 (返回)** | 弹窗 / 排序模式 | 退出当前操作弹窗、取消应用排序并保存当前状态 |
| **X 键 (短按)** | 应用卡片区 | 进入当前 Tab 内部的应用**拖拽排序模式**，使用方向键自由换位 |
| **X 键 (长按)** | 应用卡片区 | 呼出当前 Tab 的**批量增删分类应用**弹窗，支持全拼/首字母即时搜索与勾选 |
| **Y 键 (长按)** | 应用卡片区 | 呼出**应用专属操作菜单**（Tab 迁移、系统属性详情、VideoShader 滤镜、移除图标） |
| **通知栏磁贴 (Tile)** | 系统下拉栏 | 下拉点击 **VideoShader 滤镜** 磁贴，在任何前台游戏内随时秒级开关着色器 |

---

## ⚡ 核心技术架构 (Architecture Highlights)

```
com.odin.desktop
├── data/
│   ├── db/                 # Room Database (Tab 分类、应用排序、Shader 配置)
│   ├── entity/             # 数据实体定义与 DAO
│   └── repository/         # 硬件服务与数据仓库 (App, Hardware, Shader)
├── shader/
│   ├── engine/             # VideoShaderEngine 掌机生命周期全局调度中心
│   ├── pipeline/           # IVideoShaderPipeline 接口体系
│   │   ├── AgslVideoShaderPipeline.kt  # Android 15 AGSL 硬件着色器 (GameNative 算法)
│   │   └── slang/          # RetroArch .slang / SPIR-V 多通道着色器未来扩展桩
│   ├── overlay/            # TYPE_APPLICATION_OVERLAY 零延迟穿透全屏悬浮层
│   └── model/              # AppShaderConfigEntity 配置实体
├── service/
│   ├── fan/                # AppMonitorAccessibilityService 前台应用变化嗅探
│   └── tile/               # Quick Settings 磁贴服务 (VideoShader 开关)
└── ui/
    ├── components/         # 掌机级 Compose 组件库 (ConsoleModalDialog, 卡片滑带)
    ├── navigation/         # FocusZone 掌机绝对手柄焦点管理核心
    └── viewmodel/          # LauncherViewModel 单一可信数据源与状态机
```

* **纯原生 Android 15 架构**：针对骁龙 8 Gen 2 / 8 Gen 3 平台深度适配，支持 120Hz 高刷新率。
* **零侵入式无感体验**：系统详情跳转遵循 Android 规范，绝不私自篡改系统包，保证数据绝对安全与可恢复性。
* **硬件级穿透悬浮层**：悬浮窗配置 `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_NO_LIMITS`，完全不劫持游戏手柄与触控事件。

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

