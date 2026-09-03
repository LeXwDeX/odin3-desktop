package com.odin.desktop.shader.overlay

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import com.odin.desktop.shader.model.AppShaderConfigEntity
import com.odin.desktop.shader.pipeline.AgslVideoShaderPipeline
import com.odin.desktop.shader.pipeline.IVideoShaderPipeline

/**
 * 掌机专属低延迟着色器覆盖层画布视图
 * 承载硬件加速 Shader 渲染管线，支持动态扫描线循环刷新与静态 0 功耗保持
 */
class ShaderOverlayView(context: Context) : View(context) {

    private var pipeline: IVideoShaderPipeline = AgslVideoShaderPipeline()
    private var startTimeMs: Long = SystemClock.uptimeMillis()
    private var currentConfig: AppShaderConfigEntity? = null

    init {
        // 强制开启硬件加速
        setLayerType(LAYER_TYPE_HARDWARE, null)
        pipeline.init(context)
    }

    fun applyConfig(config: AppShaderConfigEntity) {
        currentConfig = config
        pipeline.updateConfig(config)
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val config = currentConfig ?: return
        if (!config.isEnabled) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val elapsedSeconds = (SystemClock.uptimeMillis() - startTimeMs) / 1000f
        pipeline.onDraw(canvas, w, h, elapsedSeconds)

        // 仅在启用“动态扫描线”时才持续请求下一帧重绘，静态模式绘制一次即休眠，达到 0 额外能耗
        if (config.isDynamic) {
            postInvalidateOnAnimation()
        }
    }

    fun release() {
        pipeline.release()
    }
}
