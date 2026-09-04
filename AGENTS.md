# Odin 3 Desktop

AYN Odin 3 的 Android 桌面应用，使用 Kotlin、Jetpack Compose 和 Gradle Wrapper。

## 项目约定

用户于 2026-09-04 授权“以后本项目全部自动”：在指定任务范围内自主完成实现、修复、必要验证、文档、Git 提交和推送，无需逐步重复确认；用户后续具体指令优先。

- 代码、测试和文档默认提交并正常推送到 [LeXwDeX/odin3-desktop](https://github.com/LeXwDeX/odin3-desktop) 的 `origin/main`，完成后核对远端提交与本地状态。保留已有修改；强制推送、覆盖他人历史和清空用户数据不在持续授权范围内。
- 签名私钥、配对令牌、账户凭据、游戏与存档不进入 Git。
- 主代理为 GPT-6（Astra）时，具体编码任务的子代理模型可默认选择 GPT-5.6（Sol 或 Terra）。

## 按需阅读

- 构建、验证或设备调试前，读 [开发与设备验证](docs/development.md)。
- 修改性能、风扇或 Home/返回行为前，读 [修复与交接记录](docs/performance-fan-home-fixes.md)，包括设备重连后的待验项。
- 修改、启动或排查硬件桥前，读 [桥接说明](tools/hardware-bridge/README.md)。
- 功能与交互说明见 [README](README.md)。

<!-- codebase-memory-mcp:start -->
## 代码发现

代码结构查询优先使用 `codebase-memory-mcp`。会话开始或压缩恢复后，先读 [图谱验证流程](docs/code-discovery.md)，确认最近项目与索引代次；默认采用 Tier 2。每个作为证据的文件都要检查索引覆盖，存在缺口时直接核验对应源码。
<!-- codebase-memory-mcp:end -->
