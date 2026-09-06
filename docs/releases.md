# APK 发布

仓库的 [Release APK](https://github.com/LeXwDeX/odin3-desktop/actions/workflows/release-apk.yml) 工作流在 GitHub 托管的 Ubuntu 24.04 runner 上构建，不需要连接掌机或保持开发电脑在线。

## 发布方式

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

手动发布绑定启动工作流时的准确提交；已有标签必须指向同一提交。已有 Release（包括草稿）会被拒绝覆盖，失败时不会悄悄替换用户下载的 APK。若资产上传中断留下草稿，先检查失败日志，再明确删除该草稿后重跑，或使用新版本。

## 构建与版本

每次 `main` 推送和 PR 都运行 Android CI：JDK 17、SDK 35、Debug/Release 构建、单元测试、Lint、风扇/UI/Home/数据库/硬件事务回归。PR 流程不接触发布签名。发布工作流重复同样检查，全部成功才签名与创建 Release。测试报告保存 14 天，签名 APK 在 Actions 保存 30 天，Release 附件不受该期限限制。

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
