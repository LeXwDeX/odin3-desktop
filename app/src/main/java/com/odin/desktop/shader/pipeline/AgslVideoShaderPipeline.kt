package com.odin.desktop.shader.pipeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build

/**
 * Compatibility overlay for the multiplicative mask in GameNative's Vulkan CRT effect.
 * Coordinates use physical output pixels. Effects that sample game pixels use another backend.
 */
class AgslVideoShaderPipeline : OverlayShaderPipeline {

    override val id: String = "gamenative_crt"

    private var runtimeShader: RuntimeShader? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var currentConfig: OverlayShaderConfig? = null

    companion object {
        // The engine explicitly sets this window alpha; the shader compensates the same value.
        const val WINDOW_ALPHA = 0.8f

        private const val AGSL_SHADER_CODE = """
            uniform float2 uResolution;
            uniform float uWindowAlpha;

            vec4 main(vec2 fragCoord) {
                const float PI = 3.14159265;
                vec2 resolution = max(uResolution, vec2(1.0));
                vec2 uv = fragCoord / resolution;
                float scanline = 0.86 + 0.14 * sin(uv.y * resolution.y * PI);
                float grille = 0.94 + 0.06 * sin(uv.x * resolution.x * PI);
                float factor = scanline * grille;
                float alpha = clamp((1.0 - factor) / uWindowAlpha, 0.0, 1.0);
                return vec4(0.0, 0.0, 0.0, alpha);
            }
        """
    }

    override fun init(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                runtimeShader = RuntimeShader(AGSL_SHADER_CODE)
                runtimeShader?.setFloatUniform("uWindowAlpha", WINDOW_ALPHA)
                paint.shader = runtimeShader
            } catch (e: Exception) {
                runtimeShader = null
                paint.shader = null
                android.util.Log.e("AgslVideoShaderPipeline", "Could not create CRT mask", e)
            }
        }
        return runtimeShader != null
    }

    override fun updateConfig(config: OverlayShaderConfig) {
        currentConfig = config
    }

    override fun onDraw(canvas: Canvas, width: Float, height: Float, timeSeconds: Float) {
        val config = currentConfig ?: return
        val effects = config.effects
        if (!config.enabled || effects.requiresFrameInput || !effects.enableCRT) return
        if (width <= 0f || height <= 0f) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtimeShader != null) {
            val shader = runtimeShader ?: return
            shader.setFloatUniform("uResolution", width, height)
            canvas.drawRect(0f, 0f, width, height, paint)
        }
    }

    override fun release() {
        runtimeShader = null
        paint.shader = null
        currentConfig = null
    }
}
