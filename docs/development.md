# 开发与设备验证

## 构建与本地验证

使用完整 JDK 17、Android SDK platform `android-35` 和 build-tools `35.0.0`。将 `JAVA_HOME` 指向 JDK，将 `ANDROID_HOME` 或本地 `local.properties` 的 `sdk.dir` 指向实际 SDK；本机环境配置与构建产物不提交到 Git。

在仓库根目录构建 Debug APK：

```sh
./gradlew :app:assembleDebug
```

首次构建需要联网下载依赖。APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

按改动范围选择验证：性能、风扇与 Home/返回的 Python 3 回归命令及缓存前提见 [修复记录](performance-fan-home-fixes.md)；硬件桥构建和 JVM 自检见 [桥接说明](../tools/hardware-bridge/README.md)。纯文档修改检查内容和链接，无需无关的全量测试。

多语言实现与设备验收见 [多语言说明](languages.md)，扩展接口见 [架构审计](architecture.md)。`./gradlew :app:testDebugUnitTest` 在 Robolectric 的 Android 12L / 15 环境中检查语言匹配、切换与旧版上下文刷新；首次运行需要下载测试依赖。`python3 tools/architecture-regression.py` 验证语言资源、分类身份、实际数据库迁移 SQL 和 Shader 预设；发布构建还需运行 `:app:assembleRelease :app:lintDebug`。首次 Lint 需要联网获取工具依赖。Room schema 导出文件应随迁移提交。

## 设备调试授权与验收

- 项目应用调试已获准唤醒设备、切换前台和操作应用，无需逐步重复确认；用户当前明确的暂停或限制优先。
- 操作前运行 `adb devices` 重新确认目标，后续命令使用已确认的序列号，不沿用文档中的历史设备编号。
- 覆盖安装保留应用数据。签名不匹配时安全迁移原调试 keystore，不通过卸载或清数据绕过；私钥不进入 Git。
- 覆盖设备配置前备份确切目标文件，交付时说明备份位置；Root、刷机、分区变更、恢复出厂设置需要针对具体操作另行授权并约定恢复方案。
- 设备未连接时完成本地工作，明确记录尚未安装、尚未实机验证的项目。构建或替身回归通过不代表设备行为已验收。
- 性能与风扇验收同时核对界面和设备读回；OEM 异步响应、PWM 与实际转速的证据边界见 [修复记录](performance-fan-home-fixes.md) 和 [桥接说明](../tools/hardware-bridge/README.md)。
