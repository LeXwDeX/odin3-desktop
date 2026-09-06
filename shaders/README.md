# 保留的 Shader 原始文件

按用户要求，保留本项目移除 Shader 功能前的着色器源码，方便以后研究或适配模拟器。文件放在 APK 资源目录之外，不恢复应用的滤镜入口、后台引擎或画面接入功能。

## 文件与来源

[`original/gamenative/`](original/gamenative/) 中的 14 个文件逐字节恢复自提交 `18cc99d719c5db823d0b4e0c80a0474f237ed255` 的 `app/src/main/assets/shaders/`，没有修改算法、参数、文件名或许可证：

- `opengl/*.frag`：CRT、颜色、FXAA、NTSC、缩放、Toon、Vivid，共 7 个片段着色器。
- `vulkan/window.frag`：原有 Vulkan 算法家族的 GLES 适配版本。
- `fullscreen.vert`、`copy.frag`：原顶点着色器与直通复制着色器。
- `upstream.json`：上游版本、原始路径与摘要。
- `licenses/`：随原文件保存的来源说明和许可证。

这里的“原始”指本项目移除前的版本；它们此前已经适配过 GameNative / Winlator，不等于上游未经修改的文件。文件内的历史路径注释保持原样；完整说明见仓库的 [第三方来源记录](../THIRD_PARTY_NOTICES.md)。

[`original/SHA256SUMS`](original/SHA256SUMS) 记录归档文件的摘要。在 macOS 上可核对：

```sh
cd shaders/original
shasum -a 256 -c SHA256SUMS
```

## 关于模拟器导入

这些 `.frag` / `.vert` 文件使用 GLSL，仍保留原宿主的纹理、uniform 和输入输出约定。本次只归档源码，尚未转换为 PSP / PS2 模拟器的导入包，也未验证直接导入兼容性。当前计划在 PSP 模拟器和 ARMSX2 中由用户自行尝试导入，暂不修改文件或制作专用适配包。
