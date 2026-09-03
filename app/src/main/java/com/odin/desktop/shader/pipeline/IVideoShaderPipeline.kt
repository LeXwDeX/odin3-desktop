package com.odin.desktop.shader.pipeline

import android.content.Context
import android.graphics.Canvas
import com.odin.desktop.shader.model.AppShaderConfigEntity

/**
 * 掌机 VideoShader 渲染管线核心抽象接口
 *
 * 设计说明：
 * 为未来支持跨平台着色器管线（如 RetroArch .slang / Vulkan 多 Pass / SPIR-V 反射）提供统一插拔契约。
 * 当前默认实现为 AGSL 硬件加速管线 (AgslVideoShaderPipeline)，可在后续平滑挂载 SlangVulkanShaderPipeline。
 */
interface IVideoShaderPipeline {
    /** 管线唯一标识符 */
    val id: String

    /** 人类可读名称 */
    val displayName: String

    /** 初始化 GPU/着色器上下文资源 */
    fun init(context: Context)

    /** 应用最新的分应用着色器配置 */
    fun updateConfig(config: AppShaderConfigEntity)

    /**
     * 执行每一帧的着色渲染 (直接绘制到 Overlay Canvas 或底层 Surface)
     * @param canvas 绘制画布
     * @param width 屏幕或视口宽度
     * @param height 屏幕或视口高度
     * @param timeSeconds 从启动算起的时间秒数（用于动态扫描线动画）
     */
    fun onDraw(canvas: Canvas, width: Float, height: Float, timeSeconds: Float)

    /** 释放管线占用的着色器程序与显存对象 */
    fun release()
}
