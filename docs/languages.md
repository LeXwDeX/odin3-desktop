# 中文、英文、日文与 CONFIG 语言选择

默认选择“跟随系统”。设备系统语言匹配中文、英文或日文时使用对应文案；没有匹配语言时使用完整英文资源。中文目前统一使用简体文案，`zh-CN` / `zh-SG` / `zh-TW` / `zh-HK` 都会匹配中文；尚未提供独立的繁体翻译。

进入 **CONFIG（设置）→ 6. 语言**，可选择：

| 选项 | 行为 |
| --- | --- |
| 跟随系统 | 清除应用语言覆盖，继续响应设备的系统语言变化 |
| 中文 | 固定使用中文 |
| English | 固定使用英文 |
| 日本語 | 固定使用日文 |

触屏点击即可切换；手柄在左侧选择“语言”，按右键或 A 进入，使用上下键选择、A 确认，B 返回。语言名称始终使用各语言自己的写法，误选后也能找到熟悉的选项。左侧菜单与语言选项支持滚动，手柄焦点会进入可见区域。切换会刷新界面，CONFIG 的位置与状态由保留的 ViewModel 继续持有。

## 实现约定

- `AppLanguage` 通过 `AppCompatDelegate.setApplicationLocales` 设置语言，空 locale 列表表示跟随系统。Android 13+ 使用系统应用语言设置并与系统设置页同步；Android 10–12 使用 AndroidX 的 `autoStoreLocales` 持久保存。
- 三个带界面的 Activity 使用 `AppCompatActivity` 和兼容的无操作栏主题。Android 10–12 的 Application、息屏入口、通知服务与磁贴使用 `AppLanguageContext`，启动时恢复保存的语言，避免界面语言与后台文案不一致。缓存的资源随语言和系统配置变化失效；切换后通知、频道与磁贴刷新文字。
- `values/strings.xml` 是英文兜底；`values-b+zh+Hans` 和 `values-b+zh+Hant` 使用同一套中文文案；`values-ja` 包含完整日文。三种语言各有 337 条可翻译文案，另外 3 条语言名称标记为不翻译。
- `locales_config.xml` 声明三种应用语言；Gradle 限制 APK 中的语言资源，避免依赖库携带的其他语言影响语言匹配。App Bundle 关闭语言拆分，三种语言随安装包提供，支持离线切换。
- 分类身份、排序、应用映射和用户输入的名称保持独立于展示语言，本次无需数据库迁移。

API 依据见 [Android 应用语言文档](https://developer.android.com/guide/topics/resources/app-languages?hl=en)。

## 本地验证与设备验收

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
python3 tools/architecture-regression.py
python3 tools/home-back-regression.py
```

Robolectric 测试使用 Android 12L（API 32）和 Android 15（API 35）的资源与框架模拟环境，验证地区语言匹配、法语/德语/韩语的英文兜底、应用语言写入和清除、旧版 Application 资源刷新与 Activity 重建后的语言保留。架构回归检查翻译 key、格式参数、中文脚本资源一致性、三语分类名称及原有 SQL 迁移和 Shader 预设契约。Home/返回键回归检查重复输入与生命周期工作量。

2026-09-05 本地结果：6 项语言测试全部通过，337 条三语文案与格式参数检查通过，架构和 Home/返回键回归通过；Debug / Release 构建通过，Lint 为 0 个错误、65 个警告。警告主要是依赖更新建议、旧版 API 兼容分支和已有界面/资源提示；应用语言拆包问题已修正。Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

2026-09-05 本轮开始时 `adb devices -l` 没有连接设备。本地测试不等于实际掌机验收；尚未覆盖安装或取得新截图。设备重连后需覆盖安装 Debug APK，核对下列场景：

1. 系统语言为中/英/日及不支持语言时的首次启动；手动选择语言后重开应用、重启设备仍保留；切回“跟随系统”后再次响应系统语言改变。
2. CONFIG 中用触屏和手柄切换语言，当前语言勾选、菜单焦点和滚动位置正常；较大字体下语言选项保持可达。
3. 桌面、设置、应用管理、滤镜校准及预览、运行中通知和磁贴的三语文案与布局；用户自定义分类名和应用排序保持原样。
4. 继续按 [性能、风扇与 Home/返回修复记录](performance-fan-home-fixes.md) 完成原有的设备待验项。
