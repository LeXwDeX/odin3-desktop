---
name: es-de-rom-scrape-repair
description: 整理和修复 Android 版 ES-DE 游戏库。适用于 ROM 目录与中文名称、ScreenScraper 失败、BIOS/补丁迁移，以及用本地素材生成或修复 ES-DE Miximage。
metadata:
  short-description: 修复 ES-DE 目录、中文名称与刮削失败
---

# ES-DE ROM Scrape Repair

在不删除游戏、不破坏存档的前提下，整理 ES-DE Android 游戏库并建立可重复验证的 ScreenScraper 修复流程。

## 安全边界

- 先读取设备状态、目录和配置，再决定修改方案。
- 移动文件前验证源文件存在、目标不存在；使用精确路径，保留可恢复备份。
- 不解锁 bootloader、不 root、不刷机、不清除应用数据。
- `es_settings.xml` 可能含 ScreenScraper 凭据；只输出所需设置项，临时副本用完立即清理。
- ROM 删除、存档迁移和不可逆清理需要用户明确授权。

## 1. 建立反馈信号

通过 ADB 拉取当前平台的 `gamelist.xml` 和 ES-DE 日志，生成一个快速、确定性的检查：

```text
total=<游戏总数>
scraped=<有元数据数量>
missing=<缺少元数据数量>
zzz_notgame=<ZZZ(notgame)次数>
timeouts=<超时次数>
```

判断“未抓取”时：

- 以缺少 `desc`、`releasedate`、`developer` 等游戏元数据为主要信号。
- 不要仅根据 `<image>` 判断；ES-DE 可以按约定媒体路径加载图片，而不在清单中写 `<image>`。
- 输出缺失游戏的中文显示名、当前实体文件名和清单路径。
- 保存本轮缺失名单，下一轮按同一规则比较。

完成条件：存在一个数秒内可重复运行、能够精确报告缺失数量和名单的只读检查。

## 2. 暂停并备份 ES-DE

如果 ES-DE 是默认桌面，直接强制停止会被系统重新启动。先打开 Android 设置，再停止 ES-DE：

```text
打开 Android 设置
→ 强制停止 org.es_de.frontend
→ 确认进程不存在且当前前台为设置
```

修改前备份：

- `settings/es_settings.xml`
- 涉及平台的 `gamelists/<system>/gamelist.xml`
- 路径即将改变的平台媒体目录
- 记录备份时间和原始位置

备份文件使用明确后缀，例如：

```text
.before-rom-reorg-YYYYMMDD
```

完成条件：ES-DE 已停止，所有待修改配置都有设备端备份。

## 3. 规范目录与名称

推荐目录结构：

```text
模拟器ROM/
├── gba/
│   └── Game Name.gba
├── mame/
│   └── shortname.zip
├── psp/
│   └── Game Name.iso
└── psx/
    └── Game Name.pbp
```

处理规则：

- 将 `gba/ROMS/*`、`mame/roms/*` 提升到对应平台根目录。
- MAME ZIP 必须保留模拟器要求的短名称；设备 ROM、BIOS 依赖可以隐藏，但不能随意改名。
- 展平 PS1 前先检查 `.cue/.bin/.m3u` 和多碟关系；单文件 `.pbp/.chd` 才能直接移动。
- BIOS、补丁、说明文档和未解压压缩包移到模拟器自己的资料目录；它们不应作为游戏显示。
- `.rar/.7z` 通常不是可直接启动的 PS1 游戏，保留到“待整理”目录，不当作可玩 ROM。

中文显示与刮削键分离：

```text
实体文件名：ScreenScraper/No-Intro 可识别的英文 ROM 别名
gamelist <path>：新的英文实体路径
gamelist <name>：用户原始中文名称
```

建议设置：

```xml
<bool name="ScrapeGameNames" value="false" />
<bool name="ScraperSearchMetadataName" value="false" />
<bool name="ScraperIncludeFolders" value="false" />
<bool name="ScraperOverwriteData" value="false" />
<bool name="MiximageOverwrite" value="false" />
```

完成条件：清单路径全部存在、没有非必要的 `ROMS/roms` 层级、界面名称仍为中文。

## 4. 刮削与停止条件

在 ES-DE 中选择：

```text
【未抓取到游戏数据】
```

它只处理缺少元数据的游戏，不重新刮削已经成功的条目。每轮结束后重新运行反馈检查。

| 观察结果 | 处理 |
|---|---|
| 少量 `ZZZ(notgame)`，第二轮名单减少 | ScreenScraper 临时错误；最多再试一次 |
| 连续两轮是同一批缺失游戏 | 停止重复请求；修正为 ScreenScraper 实际收录的 ROM 文件别名 |
| `Timeout was reached` | 允许 ES-DE 自动重试；确认网络后只重刮缺失项 |
| 普通无结果 | 在 ScreenScraper 官方库确认游戏存在，再使用半自动宽泛搜索 |
| 官方库存在但精确查询失败 | 优先使用官方 ROM 别名或 No-Intro 名称，不只使用英文游戏标题 |
| 官方别名已确认，关闭哈希后同批缺失显著减少 | 汉化或改版 ROM 的 MD5 不在库中；备份设置后关闭哈希，只重刮缺失项 |

`ZZZ(notgame)` 是一次成功的 HTTP 响应，不是网络错误，因此 ES-DE 的网络自动重试不会处理它。ES-DE 源码也说明 ScreenScraper 有时会为有效文件随机返回该名称；再次请求可能恢复，但同一名单连续两轮不变时应视为稳定的精确匹配失败。

参考：

https://gitlab.com/es-de/emulationstation-de/-/blob/master/es-app/src/scrapers/ScreenScraper.cpp

哈希搜索是诊断分支，不是默认开关。仅当同一批游戏已使用官方别名仍连续失败，并且关闭
`ScraperSearchFileHash` 后缺失数明显下降时，才保留设置备份并将它关闭；下一轮只重刮缺失项。
若缺失数没有下降，恢复原设置，改用半自动搜索或逐项核对官方 ROM 条目。

## 5. 用离线生成器回填 Miximage

当游戏已有元数据，但 `miximages` 缺失、过时或需要由指定的本地封面、截图、Logo 重新组合时，优先使用 ES-DE 内置的 **Offline Miximage Generator**。它在设备本地合成，不需要重新抓取游戏资料。

先判断范围。离线生成器按本次在【抓取哪些平台】中勾选的平台处理所有游戏，不是按“当前高亮游戏”处理。为避免影响其他平台：

```text
主菜单 → 刮削/抓取 → 抓取哪些平台
→ 取消非目标平台，只保留目标平台
→ 混合图像设置 → 离线生成器 → 开始
```

平台选择是本次界面会话的范围控制；启动生成器前确认外层显示“已选择 1”（或用户明确要求的数量）。

### 素材放置与备份

ADB 写入前停止 ES-DE，并保留原文件。只替换目标游戏时，将原媒体改为明确、可恢复的后缀，例如 `.before-esde-offline-YYYYMMDD`，不要删除。

媒体目录、子目录和 ROM 的相对路径必须一致；媒体使用游戏文件的同名基名，扩展名改为小写图片格式：

```text
ROMs/<system>/<相对目录>/<Game>.nsp
ES-DE/downloaded_media/<system>/covers/<相对目录>/<Game>.png
ES-DE/downloaded_media/<system>/screenshots/<相对目录>/<Game>.png
ES-DE/downloaded_media/<system>/marquees/<相对目录>/<Game>.png
```

按素材实际可得性放置：

- `screenshots` 是生成游戏画面所需的主素材。
- `3dboxes` 存在时用于 3D 游戏盒；没有 3D 盒而启用 `MiximageCoverFallback` 时，`covers` 会作为盒面回退。
- `marquees` 用于 Logo；必须是透明背景时保留 PNG Alpha。
- `physicalmedia` 仅在已有卡带、光盘等原始素材时加入；生成器不会凭空制作该部件。

关键设置应先只读核对，而非盲目重置：

```xml
<bool name="MiximageGenerate" value="true" />
<bool name="MiximageOverwrite" value="false" />
<bool name="MiximageIncludeBox" value="true" />
<bool name="MiximageCoverFallback" value="true" />
<bool name="MiximageIncludeMarquee" value="true" />
```

`MiximageOverwrite=false` 让已有成图跳过，因此可以只暂时移走一个目标游戏的旧成图、运行整个平台生成器，再只得到该游戏的新输出。需要完全重做某个平台时，先由用户明确授权覆盖或逐项备份。

### 验收与停止条件

生成结束页应记录 `Generated`、`Skipped`、`Failed`。在“一个平台仅一个目标缺图、其余成图保留”的场景，预期为：

```text
Generated: 1
Skipped: <该平台已有成图的游戏数>
Failed: 0
```

随后只读检查：

- 新文件存在于 `ES-DE/downloaded_media/<system>/miximages/<相对目录>/<Game>.png`（或已选输出格式）。
- 文件时间晚于本轮开始，尺寸符合当前 Miximage 分辨率设置。
- 目标的封面、截图和 Logo 输入文件仍在；非目标游戏的现有 Miximage 未被改写。

若 `Failed` 非零，记录失败文件名和精确错误后停止，不要通过重新刮削整个游戏库来掩盖离线生成失败。

参考：

https://gitlab.com/es-de/emulationstation-de/-/blob/master/USERGUIDE.md#miximage-settings

## 6. 验收与清理

全部满足后才结束：

- 每个 `gamelist.xml` 中的 `<path>` 都对应真实文件。
- 平台目录没有意外的嵌套路径和目标文件冲突。
- 中文 `<name>` 没有被刮削结果覆盖。
- 反馈检查显示 `missing=0`，或剩余项目已有逐项说明。
- 临时日志、含凭据的配置副本和诊断脚本已清理。

最终汇报应列出：移动数量、平台游戏数量、剩余失败名单、配置变更、备份位置以及下一步唯一动作。
