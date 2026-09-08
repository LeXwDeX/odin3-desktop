# APK 发布

仓库的 [Release APK](https://github.com/LeXwDeX/odin3-desktop/actions/workflows/release-apk.yml) 工作流在 GitHub 托管的 Ubuntu 24.04 runner 上构建，不需要连接掌机或保持开发电脑在线。

## 发布方式

自 2026-09-07 起，用户要求“掌机更新，以后都更新”。后续代码迭代默认在完成提交并推送 `main` 后，核对 GitHub Releases 和远端标签，递增修订号并推送新标签；等待 GitHub 完成全部检查、构建、签名与公开发布，再更新已连接的目标掌机。该约定替代此前“确认触发即可”的交付边界；仅文档或设备验收记录更新不重复发布相同应用的新版本。

### 默认掌机更新流程

1. 核对发布工作流成功、提交与标签一致，取得本仓库正式 Release 的 APK 和 SHA-256 附件；不为安装重复本地构建。
2. 重新运行 `adb devices -l` 确认目标与授权。多设备时先确定目标；未连接时记录待安装版本，报告“已发布、未安装”，不声称已完成掌机更新。
3. 校验附件 SHA-256、应用 ID、递增版本号、非 debuggable 属性，并核对现有 APK 与新版签名。保留旧 APK；不输出或提交签名私钥及用户数据。
4. 使用 `adb -s <serial> install -r <apk>` 保留数据覆盖安装。遇到签名不匹配、版本降级或安装错误时停止安装并报告原因，不通过卸载、清数据或强制降级绕过。
5. 安装后核对真实版本及 APK 哈希、默认 HOME、启动与设置页面；按本次改动检查相关服务和硬件读回，并清理 UI 调试辅助进程。保持用户的原厂开关及其他应用禁用状态。
6. 分别报告“已触发／已发布／已安装／已实机验证”。短时检查不代替整夜充电、完整游戏或长时间稳定性验收。

设备命令、签名与恢复约束见 [开发与设备验证](development.md)。这一默认授权仅用于本项目 APK 的后续发布和设备交付，不扩展到其他应用、Root 或固件更新。

推送新的版本标签会自动创建公开 Release，并附上已签名、不可调试的 Release APK 和 SHA-256 校验文件：

```sh
git pull --ff-only origin main
git tag v0.1.1
git push origin v0.1.1
```

示例版本只使用一次；已有 `v0.1.0` 标签保留原提交，不移动旧标签。后续使用更大的版本。标签提交必须属于 `main` 历史，版本格式为 `v主版本.次版本.修订号`，不接受前导零、预发布后缀或任意路径。

也可以在 Actions → Release APK → Run workflow 中选择 `main`，填写新标签：

- `draft` 默认开启：构建完成后创建带 APK 的 Release 草稿，检查后在 Releases 中点击 Publish release。
- 关闭 `draft`：构建、测试、签名全部通过后直接发布。

手动发布绑定启动工作流时的准确提交；尚未发布的已有标签必须指向同一提交。已正式发布的版本直接跳过，保留原有 APK，也能处理发布草稿时创建标签带来的重复触发。已有草稿拒绝覆盖；若资产上传中断留下草稿，先检查失败日志，再明确删除该草稿后重跑，或使用新版本。

## 构建与版本

`main` 推送和 PR 运行 Android CI，纯 Markdown/文档更新除外：JDK 17、SDK 35、Debug/Release 构建、单元测试、Lint、风扇/UI/Home/数据库/硬件事务回归。PR 流程不接触发布签名。发布工作流重复同样检查，全部成功才签名与创建 Release。测试报告保存 14 天，签名 APK 在 Actions 保存 30 天，Release 附件不受该期限限制。待发布提交不要使用 `[skip ci]` 等提交消息标记，以免 GitHub 同时跳过标签推送事件。

`versionName` 从标签取得；`versionCode = 主版本 × 1,000,000 + 次版本 × 1,000 + 修订号 + 1`。主版本上限 2099，次版本、修订号上限 999。例如 `v0.1.1` 对应 `0.1.1 / 1002`，高于已有安装版的 `versionCode=1`，重跑同一版本也不会变化。只发布递增版本；本地构建同一版本可运行：

```sh
tools/android ./gradlew -PreleaseVersion=0.1.1 :app:assembleDebug :app:assembleRelease
```

普通本地 Release 构建仍输出 unsigned APK。CI 使用官方 `zipalign` / `apksigner` 签名，再核验应用 ID、版本、非 debuggable 标记与证书指纹，最后生成校验文件。Actions 固定到官方提交 SHA，Gradle 分发包固定 SHA-256。

## 签名配置

仓库 Settings → Secrets and variables → Actions 需要五个 Repository secrets：

| 名称 | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | keystore 文件的单行 Base64 |
| `ANDROID_STORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | 签名条目别名 |
| `ANDROID_KEY_PASSWORD` | 私钥密码 |
| `ANDROID_SIGNING_CERT_SHA256` | 已确认签名证书的 SHA-256 指纹，可含冒号 |

本项目发布沿用现有安装版的原调试 keystore，以保持覆盖升级兼容；Release APK 本身不可调试。签名文件由项目维护者离线备份，不能从 GitHub Secrets 取回明文。不要重新生成或随意换签名，否则原安装版不能直接覆盖。未来更换正式密钥需要单独设计迁移。

私钥只在签名步骤从加密 Secrets 写入 runner 临时目录，用完删除；密码通过环境变量传给签名工具。私钥、密码和原始签名文件不进入 Git、构建缓存或上传附件。只把 Secrets 配置在维护者控制的仓库，fork 需要自己的配置。

官方参考：[APK 签名工具](https://developer.android.com/tools/apksigner)、[GitHub Secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)、[GitHub CLI 创建 Release](https://cli.github.com/manual/gh_release_create)。

## 首次云端验收

2026-09-06 的 [Release APK 运行](https://github.com/LeXwDeX/odin3-desktop/actions/runs/34007765571) 已通过构建、单元测试、Lint 和所有共享回归，生成 `odin3-desktop-v0.1.1.apk`、对应 `.sha256` 文件。先创建草稿，下载 GitHub 实际附件，再次核对 `com.odin.desktop / 0.1.1 / 1002`、Android 10+、不可调试标记和与当前掌机安装版相同的证书，均通过后已[正式发布 v0.1.1](https://github.com/LeXwDeX/odin3-desktop/releases/tag/v0.1.1)。

该 APK SHA-256 为 `e6f0be783ee27990c3844b7ccb2131c963b202548d2bea68403fe74133681660`。Git 历史没有 keystore/JKS/P12 文件，当前提交与实际 APK 也未包含签名密钥文件；五项配置已保存为仓库加密 Secrets，clone 仓库不会下载这些 Secrets。后续发布仍需保护仓库写权限及维护者账户。

公开 APK 链接匿名访问返回 HTTP 200；该 GitHub 附件已通过 `adb install -r` 在 Odin3 上保留数据覆盖安装，设备读回 `0.1.1 / 1002` 且没有 `DEBUGGABLE` 标记。安装后发起 MainActivity 启动没有报错，该次日志检查没有应用崩溃；设备随后断开，未继续完成 Release 界面操作复验。灯光修复的先前实机与用户复测见 [灯光记录](joystick-light-fix.md)。

主分支 [Android CI](https://github.com/LeXwDeX/odin3-desktop/actions/runs/34008426579) 通过；另一次[重复发布验证](https://github.com/LeXwDeX/odin3-desktop/actions/runs/34008458750) 成功跳过构建、签名和上传，附件 ID、更新时间及摘要均保持不变。

## v0.1.4 掌机更新验收

2026-09-07，[v0.1.4](https://github.com/LeXwDeX/odin3-desktop/releases/tag/v0.1.4) 的[发布工作流](https://github.com/LeXwDeX/odin3-desktop/actions/runs/34073290522) 成功，标签与构建提交均为 `ed95a53a902f891ea2204dd76e25b5d12aabf08f`。下载正式附件 `odin3-desktop-v0.1.4.apk`，SHA-256 为 `ec18228c29c7e9c23a5776ee9ec2d8d55bd2429bdf758d630aec365b6c03d5c0`，与发布摘要和校验附件一致。新旧 APK 的签名证书一致，新版为 `com.odin.desktop / 0.1.4 / 1005`，不可调试。

已通过 `adb install -r` 从 v0.1.3 覆盖升级到当前连接的 Odin3 / Android 15；未卸载或清数据。设备安装路径中的 APK 哈希与正式附件一致，原应用数据目录 inode 保持不变，既有分类和语言设置仍可见。默认 HOME 保持 `com.odin.desktop`，原厂 `com.odin.odinlauncher` 仍为禁用状态。

实际打开桌面和设置，六个菜单项按 1–6 排列，没有自动风扇入口；Tab 编辑和语言内容已查看。多次读取应用运行服务为 `(nothing)`，旧 `odin_channel_fan` 在系统通知记录中已标记 `mDeleted=true`（系统可能保留已删除渠道的记录），AFK 渠道保留。UI 检查辅助进程已清理。

安装前后首次硬件读回均为 `fan_mode=0 / PWM state=0 / duty=0 / RPM=0`。随后读到智能档 `fan_mode=4 / state=1 / duty=10000`，用户确认期间手动调整过风扇或性能；该变化不作为自主抢占证据，验收没有将其改回。此次覆盖安装与短时界面检查不等于整夜充电、深度挂起或游戏负载测试。

旧 APK 与本次下载、截图保存在本机临时目录 `/private/tmp/odin-v014-install.Iwfuih/`，可能被系统清理，不进入 Git。保留旧 APK 不代表允许绕过版本检查强制降级。

## v0.1.5 图标排序与全部应用

2026-09-07，[v0.1.5](https://github.com/LeXwDeX/odin3-desktop/releases/tag/v0.1.5) 的[发布工作流](https://github.com/LeXwDeX/odin3-desktop/actions/runs/34082548889)成功，[主分支 CI](https://github.com/LeXwDeX/odin3-desktop/actions/runs/34082515459)也通过。发布标签与构建提交为 `18661ccf6c9d0455186e264638e6857686b543e0`。

正式 APK 的 SHA-256 为 `4d9936610bec0d84488c5f4357272a6ff50865754aff3cb960197f8553620f43`，与校验附件及掌机安装路径中的文件一致。包名为 `com.odin.desktop`，版本 `0.1.5 / 1006`，不可调试，新旧签名证书一致。已在重新确认连接的 Odin3 / Android 15 上通过 `adb install -r` 保留数据安装正式版；此前仅为手势验收临时覆盖安装同版本号的 Debug 构建，最终设备使用的是 GitHub 正式附件。

覆盖安装后默认 HOME 为 `com.odin.desktop/.ui.MainActivity`，原厂 `com.odin.odinlauncher` 仍禁用。长按、点选位置、连续拖拽、三种展示排序、29 个真实应用的网格与返回入口验收见 [功能记录](icon-ordering.md)。测试前后 3 个分类记录与 16 条应用归属及其原始顺序一致。所有测试图标均来自设备已有应用，没有添加分类测试数据或清空用户数据。正式附件安装后再次实际打开“＋”入口、两行网格、排序菜单与设置页，应用进程未记录 AndroidRuntime 错误，UI 调试辅助进程已清理。

旧 v0.1.4 APK、测试前后数据库/偏好备份、正式附件、校验信息、构建记录与截图保存在本机忽略目录 `.android-local/device-analysis/icon-order/`。此次范围是桌面交互与覆盖更新，不包含游戏负载或长时间运行验收。

## v0.1.6 Power 休眠退出挂机

2026-09-07，[v0.1.6](https://github.com/LeXwDeX/odin3-desktop/releases/tag/v0.1.6) 的[发布工作流](https://github.com/LeXwDeX/odin3-desktop/actions/runs/34083819580)和[对应主分支 CI](https://github.com/LeXwDeX/odin3-desktop/actions/runs/34083804152)均成功，标签与构建提交为 `2d432e0b20dfb103571623108966a5c0ebad6764`。

正式 APK 的 SHA-256 为 `769c51fe659863ee2495a235a69014c380bf792cf2f4323ee1d878ca6da3309d`，与 GitHub 发布摘要、校验附件及安装路径中的 APK 一致；包名 `com.odin.desktop`，版本 `0.1.6 / 1007`，不可调试。新旧 APK 签名一致，已保存旧 v0.1.5 APK，并在重新确认连接的 Odin3 / Android 15 上通过 `adb install -r` 保留数据覆盖安装正式附件。

安装后应用数据目录 inode、默认 HOME 与禁用应用列表和安装前一致。Power 休眠的倒计时、纯黑遮罩、唤醒不恢复三个场景已通过独立设备读回，详见 [挂机验收](afk-launcher-device-validation.md#power-休眠自动退出挂机v016)。原始证据、旧 APK 和正式附件在本机忽略目录 `.android-local/device-analysis/afk-power/`。

正式版桌面与设置页已实际打开，既有分类和设置菜单正常显示，应用进程没有记录 AndroidRuntime 崩溃。UI 调试辅助进程已停止，验收后回到原前台应用，挂机保持关闭。

## v0.1.7 多语言 UI 与全屏应用页

2026-09-08，[v0.1.7](https://github.com/LeXwDeX/odin3-desktop/releases/tag/v0.1.7) 的[发布工作流](https://github.com/LeXwDeX/odin3-desktop/actions/runs/34182478031)和[对应主分支 CI](https://github.com/LeXwDeX/odin3-desktop/actions/runs/34182473465)均成功，标签与构建提交为 `71dc6b9d811e5d0bbdb3c77f6cb30232114ee072`。功能和测试范围见 [UI 记录](ui-v017.md)。

正式附件 `odin3-desktop-v0.1.7.apk` 的 SHA-256 为 `583fef389260f189905230670697de7671cccd4532c207d7d89ad992a47ea99d`，与 GitHub 资产摘要、校验附件及安装路径中的 APK 一致。包名 `com.odin.desktop`，版本 `0.1.7 / 1008`，不可调试，新旧 APK 签名一致。保存旧 v0.1.6 APK 后，先使用同版本 Debug 构建进行交互验证，最终在重新确认连接的 Odin3 / Android 15 上以 `adb install -r` 保留数据覆盖安装 GitHub 正式附件。

正式版实测 `[+]` 打开 29 个应用的全屏库，默认显示配置下一屏可见 6 列 × 3 行图标，顶部 Tab、顶部遥测和底部硬件 Dock 隐藏；发送手柄 B 事件后返回来源分类的 `[+]` 位置。设置页、灯光菜单和方向菜单正常打开，README 的三张英文图片均来自最终正式版实机截图。

交互测试前后的数据库所有表记录一致，包括 3 个分类和 47 条应用归属；正式版安装前的全部偏好文件也与测试前逐字节一致。最终安装前后数据目录 inode 不变，原有自定义分类仍可见，默认 HOME 和禁用应用列表一致。英文截图完成后已恢复原来的 `[zh-Hans]` 应用语言和 `1.0` 字体大小，设备回到 Odin Desktop；当前应用进程没有记录崩溃，UI 调试辅助进程已停止。

旧 APK、数据保留校验、原始截图、布局与正式附件保存在本机忽略目录 `.android-local/device-analysis/ui-v017/`。Dashboard 的 6/30 秒调度由生产 Flow 的虚拟时钟测试验证；本次设备检查覆盖所改交互，不代表所有语言、字体倍数与长时间负载场景的穷尽验收。
