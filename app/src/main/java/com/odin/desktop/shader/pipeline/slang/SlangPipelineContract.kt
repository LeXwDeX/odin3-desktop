package com.odin.desktop.shader.pipeline.slang

import com.odin.desktop.shader.pipeline.IVideoShaderPipeline

/**
 * RetroArch Slang 着色器生态兼容性技术契约与规划接口
 *
 * 架构预留说明：
 * RetroArch 规范的 .slang 着色器是基于 SPIR-V / Vulkan 的多 Pass 着色器体系（如 CRT-Geom, CRT-Royale, Mega Bezel）。
 * 在 Phase 2/3 中，通过在 NDK 层编译引入 `libslang-cross` 与 Vulkan 后端：
 * 1. 解析 .slangp 预设链文件（包含 passes 数量、filter_linear、scale_type、float_framebuffer 等元数据）；
 * 2. 将 SPIR-V 字节码反射到 Vulkan PipelineState 或转译为 GLES 3.2 运行时；
 * 3. 实现此类并注册进 [com.odin.desktop.shader.engine.VideoShaderEngine]，无需修改启动台 UI 与分应用配置层。
 */
interface ISlangShaderHost : IVideoShaderPipeline {
    /** 加载本地 .slang 或 .slangp 预设包 */
    fun loadSlangPreset(presetPath: String): Boolean

    /** 获取当前 Slang 预设暴露的动态 Uniform 参数列表 */
    fun getSlangParameters(): Map<String, Float>

    /** 动态修改 Slang 着色器内部暴露的滑块参数 */
    fun setSlangParameter(paramName: String, value: Float)
}
