# APK 发布

仓库的 [Release APK](https://github.com/LeXwDeX/odin3-desktop/actions/workflows/release-apk.yml) 工作流在 GitHub 托管的 Ubuntu 24.04 runner 上构建，不需要连接掌机或保持开发电脑在线。

## 发布方式

自 2026-09-06 起，后续小版本迭代默认在完成代码提交并推送 `main` 后，核对 GitHub Releases 和远端标签，递增修订号并推送新标签。由 GitHub 执行全部发布检查、构建、签名与公开发布；确认对应工作流已触发并交付运行链接即可。除非用户另外要求，不等待发布完成或为发布重复本地构建、下载和设备安装。尚在运行时只报告“已触发”，不能报告“已发布”。

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
