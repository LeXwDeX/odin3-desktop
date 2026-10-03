# UI 重构的警告处理

本次按实际收益处理警告。基线为 `:app:lintDebug` 的 0 个错误、87 个警告。统计包含 Debug 探针；同一依赖提示会在版本目录中重复出现，条数不等于问题种类。

2026-10-03 本地完整构建通过，Lint 为 0 个错误、39 个警告。基线中的 48 条警告已修复。81 项单元测试通过，失败、错误和跳过均为 0。

## 已修复

| 基线警告 | 条数 | 修复与收益 |
| --- | ---: | --- |
| `InlinedApi` | 1 | SSID 清理改用 Android 29 已使用的未知名称标记，不引用 API 30 才公开的字段。 |
| `RedundantLabel` | 1 | MainActivity 继承应用名称，移除重复声明。 |
| `ModifierFactoryExtensionFunction` / `ModifierParameter` | 5 | 自定义 Compose 控件将 Modifier 放在第一个可选参数；测量修饰符改为 Modifier 扩展，保留调用位置。 |
| `PluralsCandidate` | 2 | 应用数量和挂机倒计时使用 plurals；四种语言共享参数契约。 |
| `ObsoleteSdkInt` | 5 | 删除 minSdk 29 下恒真的 API 26/29 分支；保留 Home role 不可用时的回退。启动图标不再使用无意义的 v26 限定。 |
| `LaunchActivityFromNotification` | 1 | 挂机通知增加明确的退出动作，由服务处理停止；通知正文不绑定服务点击目标。 |
| `UnusedResources` | 28 | 核对生产、Debug、测试和工具引用后，移除 24 个旧文案键和 4 个旧颜色。 |
| `TypographyEllipsis` | 4 | 两套中文统一使用省略号字符。 |
| `ClickableViewAccessibility` | 1 | 遮罩提供可访问的点击动作；触摸仍需双击，第一次触摸不会退出。 |

上述修复覆盖基线中的 48 条警告。SSID、通知退出、单击防误触、无障碍退出和实际复数选择由单元测试验证。资源回归核对四种语言的 255 个翻译字符串键、2 个复数资源、数量类别和格式参数。

构建插件从 AGP 8.5.2 升到 8.6.1，并移除 `android.suppressUnsupportedCompileSdk=35`。该版本正式支持 API 35，继续使用当前 Gradle 8.7 和 JDK 17。这消除了被配置屏蔽的兼容性提示；它不属于上表的 Lint 条数。[AGP 8.6 兼容性说明](https://developer.android.com/build/releases/agp-8-6-0-release-notes)

## 保留并说明原因

| 基线警告 | 条数 | 当前处理 |
| --- | ---: | --- |
| `AndroidGradlePluginVersion` / `GradleDependency` | 30 | 保留更新提示，建议单独验证工具链和依赖迁移，见下文。 |
| `PrivateApi` / `DiscouragedPrivateApi` | 6 | 生产端 1 条用于连接 Odin 固件的 `PServerBinder`；其余 5 条位于 Debug 硬件和故障探针。接口失败会回报错误；Debug 探针不进入正式 APK。删除接口会失去现有硬件功能或验证能力。 |
| `DiscouragedApi` | 2 | 启动器和 Debug 组件板面向横屏掌机；运行时仍遵守用户的方向设置。 |
| `UnusedAttribute` | 1 | `localeConfig` 为 Android 13 及以上提供系统语言入口；较低版本忽略该属性，应用内离线切换继续由 AppCompat 处理。 |

这些提示保持可见。没有增加 Lint baseline，也没有全局禁用检查。`collectAsVisibleState` 对 `StateFlowValueCalledInComposition` 的局部抑制只覆盖初值读取；后续订阅使用 `repeatOnLifecycle(STARTED)`，订阅停止和恢复由测试覆盖。

编译器仍会提示挂机遮罩的 `FLAG_FULLSCREEN` 已废弃。遮罩属于 WindowManager overlay，应用支持 Android 29；本轮保留它，以维持系统栏区域的黑屏覆盖。后续改用窗口 Insets 控制时，应先验证旧系统和原厂固件的覆盖范围。

本机首次构建还出现 SDK XML 元数据版本不一致的提示。该提示涉及本机 SDK 元数据与构建插件的版本差异。这项提示留待工具链迁移复核；不改写 SDK 包的元数据文件。

## 依赖升级建议

优先完成一次独立的工具链迁移。AGP 9 默认启用内置 Kotlin，需要调整现有 Kotlin 插件和编译配置；不能仅替换版本号。[官方迁移步骤](https://developer.android.com/build/migrate-to-built-in-kotlin)

随后逐组升级 Core、Lifecycle、Activity、AppCompat 和 Compose。新 Core 已将编译 SDK 提升到 36.1；当前工程使用 SDK 35。升级时应一起核对 SDK、Kotlin 元数据与 Compose 编译器，再验证生命周期、手柄焦点和四语言布局。[Core 发布说明](https://developer.android.com/jetpack/androidx/releases/core)

Room 升级单独验证。新版本调整 Kotlin/KSP 代码生成和数据库连接池。应核对 schema、旧库升级、事务与 Flow 行为，再做保留用户分类、排序和自定义名称的安装验收。[Room 发布说明](https://developer.android.com/jetpack/androidx/releases/room)

## 验证入口

```sh
tools/android ./gradlew --warning-mode=all --console=plain -PreleaseVersion=0.1.22 \
  :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
tools/android python3 tools/architecture-regression.py
```

正式结果和设备连接状态记入 [发布记录](releases.md)。没有连接掌机时，不将本地测试视为视觉、功耗、通知面板或无障碍服务的实机验收。

Robolectric 的服务遮罩没有实际附着到窗口。测试通过 View 的 `performAccessibilityAction(ACTION_CLICK)` 入口验证退出和锁释放；附着后导出的节点动作仍需实机核对。
