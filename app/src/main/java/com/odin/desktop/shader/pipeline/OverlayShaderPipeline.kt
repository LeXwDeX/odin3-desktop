package com.odin.desktop.shader.pipeline

import android.content.Context
import android.graphics.Canvas
import com.odin.desktop.shader.model.GameNativeShaderSettings

data class OverlayShaderConfig(val enabled: Boolean, val effects: GameNativeShaderSettings)

/** Draws a transparent overlay without access to the underlying game's pixels. */
interface OverlayShaderPipeline {
    val id: String
    /** Initialization must succeed before the host reports an active overlay. */
    fun init(context: Context): Boolean
    fun updateConfig(config: OverlayShaderConfig)
    fun onDraw(canvas: Canvas, width: Float, height: Float, timeSeconds: Float)
    fun release()
}
