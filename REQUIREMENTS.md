# Odin 3 专属应用（odin3_desktop）需求梗概与开发规范

## 一、 项目背景与定位
- **定位**：完全取代 AYN Odin 3 官方桌面（Default Home Launcher），专为掌机手柄操作量身打造的掌机启动台与系统增强应用。
- **形态**：单应用集成 Launcher 桌面、通知栏快捷磁贴（TileService）与后台硬件策略调度服务。
- **特权要求**：**完全无需 Root，亦无需 Shizuku**。全部功能基于 Android 标准权限（`category.HOME`、`WRITE_SETTINGS`、`SYSTEM_ALERT_WINDOW`、`TileService`、`WAKE_LOCK`）实现。
- **功能变更记录**：
  - 取消“后台一键强杀（kill/force-stop）”功能。
  - UI 表现层暂不展开，先完善核心领域层、持久化数据库、后台服务与通信架构。

---

## 二、 核心功能规格说明

### 1. 桌面启动台与 Tab 管理引擎（核心领域层）
- **默认桌面**：声明 `android.intent.category.HOME` 与 `android.intent.category.DEFAULT`，支持系统设置设为默认启动器。
- **Tab 分组持久化**：
  - 本地 SQLite (Room) 存储 Tab 配置与应用归类关系。
  - 支持 Tab 的完整 CRUD（新建、删除、重命名、排序、默认选中）。
  - 支持已安装应用的扫描（`QUERY_ALL_PACKAGES`）、归类入 Tab、自定排序、隐藏不需要的应用。
- **UI 预留**：留出手柄导航焦点接口（Focus Navigation Contracts），待 UI 规划确认后直接接入。

### 2. 下拉通知栏【息屏挂机】与 OLED 防烧屏（AFK 引擎）
- **下拉快捷磁贴 (Quick Settings Tile)**：
  - 实现 `TileService`，系统下拉控制中心呈现“息屏挂机”开关，一键即可触发。
- **全屏纯黑覆盖**：
  - 申请 `SYSTEM_ALERT_WINDOW` 权限，以 `TYPE_APPLICATION_OVERLAY` 弹出全屏遮罩。
  - 背景强制采用纯黑 `#000000`（OLED 像素断电熄灭，实现物理级极低功耗）。
  - 覆盖层拉低系统亮度（`screenBrightness = 0.01f`）。
- **防烧屏像素漂移（Anti-Burn-In Pixel Shift）**：
  - 挂机层展示暗灰色（如 `#333333`）极简状态（时间、电量、挂机时长、温度）。
  - 内置不规则漫游算法：周期性（如每 30 秒）在一定范围内随机平移渲染坐标，避免固定像素连续发光造成 OLED 残影或烧屏。
- **后台全速运行与防误触**：
  - 持有 `PARTIAL_WAKE_LOCK`，确保挂机时 CPU 与游戏底层进程全速运转不中断。
  - 拦截所有普通触屏手势（防误触游戏），提供特定解锁交互（如长按指定按键或双击指定区域解除挂机）。

### 3. 充电且无游戏时风扇智能停转（Fan Watchdog）
- **硬件键位**：
  - 经实机逆向确认，Odin 3 系统风扇档位位于 `Settings.System.fan_mode`（`0`: 关闭, `1`: 静音, `2`: 智能, `3`: 疾风, `4`: 自定义）。
- **调度逻辑**：
  - 监听电源广播（`ACTION_POWER_CONNECTED` / `ACTION_BATTERY_CHANGED`）。
  - 监听前台应用变动（AccessibilityService 或 UsageStatsManager）。
  - **判定规则**：
    - 设备处于充电状态，且当前前台应用**不是**游戏（例如桌面、息屏挂机、影音工具等）时，自动将 `fan_mode` 写入 `0`（关闭）。
    - 一旦检测到用户进入游戏 Tab 中的任一应用，立即切回预设散热档位（如 `2` 智能或 `3` 疾风）。
  - **过热硬保护**：
    - 持续监测电池/机身温度，即便未开游戏，若机身达到设定安全红线（如 48°C），强制开风扇散热。

### 4. 桌面 UI/UE 与手柄焦点引擎（已落地）
- **顶部 Tab 栏 (`TopTabBar`)**：
  - 物理 `L1 / R1` 肩键切换 Tab，亦可无缝切入右侧 `[ ⚙️ CONFIG ]`。
- **中部应用展示区 (`AppHorizontalRow` & `AppCard`)**：
  - 顶部大字独立展示 `HOVERED APP NAME`，消除长名称截断。
  - 单行大卡片水平滚动，焦点居中，卡片聚焦 1.10x 放大并带有青色流光边框。
  - 实体 `A` 键启动应用，实体 `X` 键弹出应用管理菜单。
- **底部硬件状态控制栏 (`BottomDockBar`)**：
  - 5 个硬件快捷开关胶囊（焦点下移直接交互）：
    1. `[ ⚡ 性能: 正常 / 性能 / 高性能 ]`
    2. `[ 🌀 风扇: 智能 / 疾风 / 静音 / 关闭 ]`
    3. `[ 💡 摇杆灯: 开启 / 关闭 ]`
    4. `[ 🔋 充电限制: 80% / 关 ]`
    5. `[ ✈️ 飞行模式: 关 / 开 ]`
- **系统设置弹窗 (`ConfigDialog`)**：
  - 1、摇杆灯颜色选择（色环调色预设，写回 `joystick_led_light_picker_color`）。
  - 2、屏幕方向规则（针对 Odin 3 底侧 USB 物理接口，仅支持 固定横屏 与 传感器横屏 两种模式）。
  - 3、Tab 页编辑（增删改查 Tab，上限 10 个）。
  - 4、关于（Odin 3 专属应用、开源许可说明）。
- **屏蔽原厂右侧侧滑菜单（GameAssistant）**：
  - 提供通过 ADB 命令禁用原厂侧滑悬浮窗的指引（`pm disable-user com.odin.gameassistant`），实现干净清爽的界面体验，且随时可逆。

---

## 三、 模块与包结构设计

```
com.odin.desktop
├── data/
│   ├── db/              # Room 数据库配置 (OdinDatabase)
│   ├── entity/          # TabEntity, AppMappingEntity
│   ├── dao/             # TabDao, AppMappingDao
│   ├── model/           # InstalledApp
│   └── repository/      # AppRepository
├── service/
│   ├── afk/             # AfkTileService, AfkOverlayService, BurnInShifterEngine
│   └── fan/             # FanWatchdogService, FanController, HardwareController, AppMonitorAccessibilityService
├── receiver/            # BootCompletedReceiver (自启动后台守护)
└── ui/
    ├── MainActivity.kt  # 沉浸式 Default Home Launcher 宿主 & 全局手柄按键分发
    ├── navigation/      # FocusZone, GamepadKeyHandler
    ├── viewmodel/       # LauncherViewModel
    ├── screens/         # LauncherScreen
    ├── components/      # TopTabBar, AppHorizontalRow, AppCard, BottomDockBar, ConfigDialog, AppOptionsDialog
    └── theme/           # Color, Theme (纯黑 OLED 掌机主题)
```
