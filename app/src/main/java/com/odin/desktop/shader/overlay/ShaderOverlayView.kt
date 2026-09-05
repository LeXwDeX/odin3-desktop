package com.odin.desktop.shader.overlay

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import com.odin.desktop.shader.model.AppShaderConfigEntity
import com.odin.desktop.shader.pipeline.AgslVideoShaderPipeline
import com.odin.desktop.shader.pipeline.OverlayShaderPipeline
import com.odin.desktop.shader.pipeline.OverlayShaderConfig

/**
 * 掌机专属低延迟着色器覆盖层画布视图
 * 承载硬件加速 Shader 渲染管线，支持动态扫描线循环刷新与静态 0 功耗保持
 */
class ShaderOverlayView(
    context: Context,
    private val pipeline: OverlayShaderPipeline = AgslVideoShaderPipeline(),
    private val onDrawn: () -> Unit = {},
    private val onFailure: (Exception) -> Unit = {}
) : View(context) {

    private var startTimeMs: Long = SystemClock.uptimeMillis()
    private var reportedDraw = false
    private var currentConfig: AppShaderConfigEntity? = null

    init {
        // 强制开启硬件加速
        setLayerType(LAYER_TYPE_HARDWARE, null)
        check(pipeline.init(context)) { "CRT overlay initialization failed" }
    }

    fun applyConfig(config: AppShaderConfigEntity) {
        currentConfig = config
        pipeline.updateConfig(OverlayShaderConfig(config.isEnabled, config.effects))
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
        try {
            check(canvas.isHardwareAccelerated) { "CRT overlay needs a hardware canvas" }
            pipeline.onDraw(canvas, w, h, elapsedSeconds)
            if (!reportedDraw) {
                reportedDraw = true
                post { onDrawn() }
            }
        } catch (failure: Exception) {
            post { onFailure(failure) }
        }
    }

    fun release() {
        pipeline.release()
    }
}
