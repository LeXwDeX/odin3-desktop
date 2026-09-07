# 🎮 Odin 3 Desktop (`odin3-desktop`)

<div align="center">

**专为 AYN Odin 3 安卓掌机深度打造的旗舰级默认桌面启动台与系统增强套件**

[![Release](https://img.shields.io/badge/Release-v0.1.5-cyan?style=for-the-badge&logo=android)](https://github.com/LeXwDeX/odin3-desktop/releases)
[![Platform](https://img.shields.io/badge/Platform-AYN%20Odin%203%20(Android%2013%2B)-black?style=for-the-badge&logo=qualcomm)](https://github.com/LeXwDeX/odin3-desktop)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20%7C%20OLED%20Black-blue?style=for-the-badge&logo=jetpackcompose)](https://github.com/LeXwDeX/odin3-desktop)

*纯粹掌机美学 • 100% 全实体手柄盲操 • 系统默认主屏幕 • 手动原厂硬件控制*

</div>

当前开发交接见 [性能、风扇与 Home/返回键修复记录](docs/performance-fan-home-fixes.md)。其中包含新机器 clone 后的构建步骤、已验证结果及设备重连后的待验项目。

问题与验收见 [问题与需求记录](docs/issues.md)：Dock 硬件控制已改为原厂服务直连；从 0.1.2 起已按用户要求完整移除 Shader，详见 [移除说明](docs/shader-removal.md)。

支持中文、英文和日文，默认跟随设备的系统语言，其他语言使用英文。进入 **CONFIG（设置）→ 5. 语言**，可选择“跟随系统 / 中文 / English / 日本語”，选择后立即刷新并自动保存；支持触屏和手柄上下选择、A 键确认。中文目前统一使用简体文案，繁体中文系统也会匹配中文。Android 13+ 与系统的应用语言设置同步，Android 10–12 使用 AndroidX 保存选择。分类身份与显示名称分离，自定义名称保持原样。实现与验收见 [多语言说明](docs/languages.md)，扩展接口见 [架构审计](docs/architecture.md)。下方截图来自此前实机版本；当前版本已移除滤镜和自动风扇入口，设置菜单现为 1–6 项，旧截图中的入口和序号不代表当前版本。

---

## 📸 掌机视觉画册 (Showcase)

### 1. 极致纯黑 OLED 掌机桌面 (Console Home)
采用全对称中轴线视网膜大卡片滑带设计，背景深度适配 Odin 3 AMOLED 屏幕纯黑低功耗特性（`#000000`）。支持毫秒级手柄摇杆光标跟随与触控操作 100% 绝对同频同步。

![掌机主界面](docs/screenshots/01_launcher_home.png)

---

### 2. 仪表盘与设备状态中心 (Dashboard)
首页最左侧的只读硬件状态与快捷操作中心。存储与 CPU/GPU 组合对齐，运行内存与 Wi-Fi 对齐：
* **内部存储 4 色分类标定**：采用 4 色独立进度条与字色标识，直观呈现空间结构：
  - 🔵 **系统空间**（`#7C4DFF` 紫蓝）：Android 系统分区及保留空间；
  - 🟢 **应用安装**（`#00E676` 翡翠绿）：用户应用及其数据文件；
  - 🟡 **其他文件**（`#FFB300` 琥珀黄）：下载、媒体与文档等其他已用数据；
  - ⚪ **空闲容量**（`#455A64` 幽灵灰）：剩余可用空间；
* **外部扩展卷**：自动枚举并展示 TF 卡卷独立容量卡片；
* **实时硬件传感器**：只读采集 CPU/GPU 物理最高温度（0~105°C 动态标度）、非系统应用 PSS 内存占用、Wi-Fi 吞吐速率；
* **三项常用操作**：文件管理（DocumentsUI）、系统设置、Odin 设置；
* **五项硬件 Dock**：性能模式、风扇档位、摇杆氛围灯、充电优化、飞行模式。

![掌机仪表盘](docs/screenshots/01_launcher_dashboard.png)

---

### 3. 系统默认主屏幕 (Default Home)
由用户在 Android 的系统选择窗口中决定默认桌面。

* **标准桌面注册**：声明 `MAIN` / `HOME` / `DEFAULT`，通过 `android.app.role.HOME` 请求默认桌面角色。选择 Odin Desktop 后，开机及按 Home 时由系统进入本桌面。
* **尊重默认选择**：选择原厂或其他桌面时，不会主动拉起 Odin Desktop。旧版“开机自启”偏好已停止读取，风扇守护和开机接收器均已移除。
* **系统选择入口**：在设置【3. 默认桌面】中点击卡片或按 A，打开系统角色选择窗口；已经是默认桌面时可进入系统设置更换。取消选择保持原桌面。

![系统默认主屏幕设置](docs/screenshots/07_config_home_boot.png)

---

### 4. 屏幕方向规则系统级生效 (Orientation Rules)
* **握持横屏（允许应用竖屏）**：横屏应用保持正常握持方向，传感器不会使画面掉头；应用明确请求竖屏时仍正常竖屏。
* **传感器横屏（自适应正反横屏）**：解除握持锁，按应用自身的方向请求和传感器旋转。
* 配置通过原厂接口写入并读回验证后显示为已启用；系统偏好持久化，不需要常驻旋转服务或电脑。实现及固件边界见 [本轮修复记录](docs/grip-fan-shader-fixes.md)。

![屏幕旋转与换向策略](docs/screenshots/05_config_orientation.png)

---

### 5. 应用专属操作控制台 (App Actions)
在桌面卡片对着任意应用按下手柄 `Y` 键即刻呼出控制台级操作浮层：
* **移动至其他 Tab 分类**：无损将图标迁移至其他自定义分组；
* **进入应用属性详情**：一键直达系统设置应用详情页（管理权限、存储与安全卸载）；
* **从当前分类移除**：从当前 Tab 移出图标（不影响应用本身安装）。

![应用专属操作面板](docs/screenshots/02_app_actions.png)

---

### 6. 模块化 Tab 自定义分组编辑 (Tab Management)
彻底告别乱糟糟的手机式应用抽屉。为掌机用户量身打造分组管理体系：
* 支持自由创建新分组、修改名称、标记为「游戏分类」；
* 支持手柄光标快速调整分组显示顺序（上移/下移）；
* 支持指定任意分组为「默认首页」；
* 在应用列表界面长按 `X` 键支持全量应用快速检索与批量勾选归类。

![Tab 分组与分类编辑](docs/screenshots/06_config_tabs.png)

---

### 7. 摇杆 RGB LED 氛围灯 (LED Customization)
提供 6 种经过色彩校准的摇杆 LED 氛围灯预设（青蓝、极客紫、战斗红、荧光绿、冰川白、暗夜灰）：
* 手柄光标左右切换，按 A 键即时生效并写入硬件；
* 优化响应管线，极速触发，受限硬件后端双向安全读回确认。

![摇杆 RGB LED 灯光控制](docs/screenshots/04_config_led.png)

---

## 🎨 设计哲学与色彩体系 (Design Philosophy)

系统统一遵循直观严谨的掌机状态色彩层级：

| 色彩定义 | 状态含义 | 典型应用场景 |
| :--- | :--- | :--- |
| **幽灵灰 (`#757575`)** | **关闭 / OFF / 空闲** | 功能关闭、开关处于 OFF 状态、磁盘空闲空间 |
| **翡翠绿 (`#00E676`)** | **安全 / 开启 / 正常** | 功能正常启用、已设为默认桌面、性能默认档、应用已安装占用 |
| **琥珀黄 (`#FFD54F`)** | **警告 / 中度性能** | 性能模式、9V/3A 充电模式、未设为默认主屏幕 |
| **战斗红 (`#FF5252`)** | **最高 / 严重 / 极限** | 高性能模式、风扇最大档、充电分离激活 |
| **电光蓝 (`#00E5FF`)** | **特殊状态 / 高亮聚焦** | 当前手柄光标聚焦 |

### 防抖与 0ms 极速手感
针对掌机手柄高频连续按键场景，底层采用**乐观更新 (Optimistic UI) + 协程防抖通道 (Debounce & Coalesce)**。连续点击时界面 0ms 瞬间翻转响应，后台硬件控制指令聚合平滑下发，彻底消除界面反复闪烁与回跳。

---

## 🕹️ 全硬件实体按键交互指南 (Gamepad Controls)

每个分类支持“手动排序 / 安装时间（最近优先）/ 最后运行时间（最近优先）/ 应用名称（A–Z）”，点击右下角“排序”或按 START 选择，按分类自动保存。名称使用当前界面语言的排序规则；最近运行使用 Android 提供的使用记录，未授权时提供系统授权入口，没有记录的应用排在最后。

长按图标直接进入排序：可以点选图标后再点目标位置，也可以长按后拖拽移动，拖到边缘会自动滚动。按“完成排序”、Y 或 B 退出；手动调整切换为手动顺序，并保存整个分类。原有应用管理仍可通过右下角按钮或长按 Y 打开。

普通分类滑带最多显示前 **20 个图标**；超过 20 个时，第 21 个位置为 **“＋ 全部应用”**。点击或用 A 打开全部已安装应用的网格，支持触摸滚动、手柄四向导航、排序和拖拽，返回时恢复原分类与光标位置。编辑排序时展示整个分类，可把第 20 个之后的图标移到首页。

【全部应用】的初始手动顺序继续采用首次安装时间从新到旧，新安装应用排在保存的顺序之前，更新不会按更新时间置顶。详细行为与验收见 [图标排序与全部应用](docs/icon-ordering.md)。

| 按键 / 组合 | 触发场景 | 交互行为 |
| :--- | :--- | :--- |
| **D-Pad / 摇杆 (左右)** | 桌面大卡片 | 在应用卡片之间水平平滑循环导航，带弹性视网膜动效 |
| **D-Pad / 摇杆 (上下)** | 桌面主层 | 在顶部 Tab 栏、中部应用区/常用操作、底部 Dock 之间移动焦点 |
| **L1 / R1** | 桌面主层 | 向左 / 向右循环切换 Dashboard 与各个应用 Tab 分组 |
| **A 键 (确认)** | 全局通用 | 启动选中应用；执行菜单选项；循环 Dock 硬件状态；确认设置项 |
| **B 键 (返回)** | 全局通用 | 退出当前操作弹窗；取消排序；从设置子菜单返回左侧导航 |
| **X 键** | 桌面卡片区 | 呼出当前 Tab 的**批量增删应用抽屉**（支持全拼/首字母即时搜索与勾选） |
| **X 键** | Dock 充电卡片 | 切换**充电分离模式**（开启红 / 关闭灰） |
| **Y 键 (长按/按键)** | 桌面卡片区 | 呼出**应用专属操作菜单**（Tab 迁移、系统属性、移除图标） |
| **START 键** | 桌面卡片区 / 全部应用网格 | 打开展示排序；方向键选择，A 确认，B 返回 |
| **Y 键 (短按)** | 桌面卡片区 | 开启/退出当前分类卡片的手动自由排序模式 |
| **Home 键** | 系统任何位置 | 返回系统选定的默认桌面 |

---

## ⚡ 核心技术架构 (Architecture Highlights)

```
odin3_desktop/
├── app/src/main/
│   ├── AndroidManifest.xml     # HOME Launcher、AFK 服务与 QS 磁贴核心声明
│   ├── java/com/odin/desktop/
│   │   ├── OdinDesktopApplication.kt   # 全局单例与 Room 数据库初始化
│   │   ├── dashboard/                  # 只读统计、存储多卷探测与三项常用操作
│   │   ├── data/                       # Room 数据库实体、DAO 与多源数据仓库
│   │   ├── service/
│   │   │   ├── afk/                    # 息屏挂机 OLED 纯黑防烧屏浮层服务
│   │   │   └── fan/                    # 手动硬件事务与温度遥测
│   │   └── ui/
│   │       ├── MainActivity.kt         # 默认桌面主入口、沉浸式全屏与按键路由
│   │       ├── components/             # BottomDockBar、DashboardContent、ConfigDialog、TopTabBar
│   │       ├── navigation/             # FocusZone 掌机绝对焦点管理器与 GamepadKeyHandler
│   │       ├── screens/                # LauncherScreen 顶级响应式组合布局
│   │       ├── theme/                  # 纯黑 OLED 主题色彩体系
│   │       └── viewmodel/              # LauncherViewModel 状态机与硬件控制聚合通道
│   └── res/
└── tools/hardware-bridge/              # 旧桥开发诊断与共享硬件事务自检
```

* **Android 13+ & Jetpack Compose**：专为 AYN Odin 3 掌机横屏高刷 OLED 定制，全矢量硬件加速渲染；
* **原厂硬件服务直连**：应用内通过 `PServerBinder` 执行固定控制并核验、回滚，无需电脑启动桥接或用户 Root；
* **纯黑 OLED 保护**：全界面 `#000000` 像素发光优化，息屏挂机浮层配合微位移防烧屏引擎。

---

## 📦 编译构建与安装部署 (Build & Install)

### 环境要求
* OpenJDK 17
* Android SDK (API 35 / Build-Tools 35.0.0)
* AYN Odin 3 掌机设备 (开启 USB 调试)

### 本地编译与安装
Apple Silicon Mac 可直接使用项目内环境，无需 Homebrew。SDK、JDK、缓存和签名留在忽略目录，不进入 Git；其他环境见 [开发说明](docs/development.md)。

```bash
python3 tools/setup-android.py
tools/android ./gradlew :app:assembleDebug
tools/android adb devices -l
tools/android adb -s <设备序列号> install -r app/build/outputs/apk/debug/app-debug.apk
tools/android adb -s <设备序列号> shell am start -n com.odin.desktop/.ui.MainActivity
```

### GitHub 自动发布 APK

推送 `v主版本.次版本.修订号` 标签后，GitHub Actions 自动构建、测试、签名，并将 APK 和 SHA-256 校验文件发布到 [Releases](https://github.com/LeXwDeX/odin3-desktop/releases)。也可以在 [Release APK](https://github.com/LeXwDeX/odin3-desktop/actions/workflows/release-apk.yml) 中手动运行，默认先生成草稿。用户下载 APK 即可安装，无需开发环境或连接电脑。签名、版本规则和操作步骤见 [发布说明](docs/releases.md)。

---

## 📜 许可与致谢 (Credits & License)

* **AYN Odin 社区**：专为追求极致纯粹安卓掌机体验的硬核玩家打造。

旧版 Shader 的来源与许可记录保留在 [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES.md)。仓库尚未包含第一方代码的统一 LICENSE 文本。

按用户要求，Shader 原始 GLSL 文件、来源记录和许可证另行保存在 [shaders/](shaders/README.md)，不打包进 APK。本次只保留源码，PSP / PS2 模拟器导入适配尚未进行。


## 顶部状态

设置按钮左侧显示电池电量、充电状态、风扇转速和 PWM，占用固定区域；TAB 列表相应缩窄，底部 Dock 保持原布局。桌面不可见时停止采样，不可读的数据显示“—”。

## 风扇控制与后台运行

风扇只提供手动“关闭／智能／最高”档位；“智能”是固件自身的智能散热档，不是应用自动接管。应用不再根据充电、息屏、温度或游戏状态自动改档，也不再声明风扇守护、前台应用无障碍监控或开机接收器。升级仅删除旧自动风扇偏好与通知渠道，不恢复或重写设备当前档位。

用户主动切换性能时仍保留既有散热联动：手动最高档保持，其他档位在默认性能下关闭、升高性能时使用智能散热。原厂温控保护不变；原厂 USB 充电风扇开关是独立系统设置，不由应用修改。

**床头充电／按电源键息屏时的推荐设置：** 如果不希望接入充电时被原厂策略自动切到智能风扇档，请在 **Odin 设置 → USB 设置 → USB 连接充电时的风扇状态设置** 中关闭该开关。开启时，原厂策略可在充电状态变化时自动切换风扇档位，覆盖此前的手动选择；这不是奥丁控制台的风扇守护。关闭仅取消这一路原厂充电联动，不是关闭温控保护，也不保证所有场景永远停扇；游戏或高负载时仍应选择合适的散热档位。该路径已在 Odin 3 固件 `Odin3_V1.0.0.187_20260616_193307_user` 核对，其他固件的名称或行为可能不同。开关由用户自行选择，安装和升级本应用不会代为修改。

手动硬件按钮没有常驻前台服务或风扇通知。只有显式开启 AFK 息屏挂机时，才会运行其浮层前台服务并出现对应系统提示。桌面隐藏后停止顶部和仪表盘采样；温度、RPM/PWM 显示保留。删除范围、升级回归与原厂调查见 [风扇控制器记录](docs/fan-controller-ownership-investigation.md)。

## 硬件服务如何启用

在已验证的 Odin 3 固件上，安装并打开应用即可使用底部硬件控制。`PServerBinder` 由固件启动并注册，我们的应用通过 Android Binder 获取它，在应用内管理性能、风扇、摇杆灯、充电和飞行模式。设备重启后重新取得该接口即可，不需要用户连接开发电脑、启动 ADB 桥、开启无线调试或安装其他授权应用。

应用仍以普通应用身份运行，由原厂服务执行其有权限的操作。内置适配层只接受固定控制，写入后核对实际性能属性、风扇 PWM 或对应系统设置，失败时恢复原值。复制原厂服务二进制到 APK 不会继承系统身份，因此不能靠复制文件提供独立 fallback；我们内置的是协议适配和恢复逻辑。

2026-09-06 已在 Android 15、`Odin3_V1.0.0.187_20260616_193307_user` 固件验证重启后控制，并在撤销旧调试写设置授权时完成往返与恢复。旧电脑桥已退出应用运行路径，APK 也移除了原桥所需的联网权限。厂商私有接口的适配范围以实测固件为准，其他功能的统计权限仍各自管理。详细过程、测试与证据边界见 [原厂硬件服务接入记录](docs/hardware-standalone-investigation.md)。

握持方向也经由同一原厂接口设置：关闭 `force_landscape` 顶层强制浮层，设置 `persist.demo.rotationlock` / `persist.demo.remoterotation` 旋转偏好，再按现有显示尺寸刷新 Android 旋转策略，保留应用的竖屏请求和用户已有分辨率。选择传感器模式会解除该偏好锁；失败会恢复原值，不通过隐藏权限异常假报成功。
