package com.odin.desktop.shader.pipeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import com.odin.desktop.shader.model.AppShaderConfigEntity

/**
 * 100% 遵循 GameNative (com.winlator.renderer.effects.CRTEffect) 的扫描线着色器实现。
 * 完全对齐 GameNative 算式与常量，不添加多余参数。
 */
class AgslVideoShaderPipeline : IVideoShaderPipeline {

    override val id: String = "gamenative_crt"
    override val displayName: String = "GameNative CRT 扫描线"

    private var runtimeShader: RuntimeShader? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var currentConfig: AppShaderConfigEntity? = null

    companion object {
        // 完全遵循 GameNative CRTEffect.java:
        // #define SCANLINE_INTENSITY_X 0.125
        // #define SCANLINE_INTENSITY_Y 0.375
        // #define SCANLINE_SIZE 1024.0
        // float scanlineX = abs(sin(vUV.x * SCANLINE_SIZE) * 0.5 * SCANLINE_INTENSITY_X);
        // float scanlineY = abs(sin(vUV.y * SCANLINE_SIZE) * 0.5 * SCANLINE_INTENSITY_Y);
        // gl_FragColor = vec4(mix(finalColor.rgb, vec3(0.0), scanlineX + scanlineY), finalColor.a);
        private const val AGSL_SHADER_CODE = """
            uniform float2 uResolution;

            vec4 main(vec2 fragCoord) {
                vec2 uv = fragCoord / uResolution;

                const float SCANLINE_SIZE = 1024.0;
                const float SCANLINE_INTENSITY_X = 0.125;
                const float SCANLINE_INTENSITY_Y = 0.375;

                float scanlineX = abs(sin(uv.x * SCANLINE_SIZE) * 0.5 * SCANLINE_INTENSITY_X);
                float scanlineY = abs(sin(uv.y * SCANLINE_SIZE) * 0.5 * SCANLINE_INTENSITY_Y);

                float dark = scanlineX + scanlineY;
                return vec4(0.0, 0.0, 0.0, clamp(dark, 0.0, 1.0));
            }
        """
    }

    override fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                runtimeShader = RuntimeShader(AGSL_SHADER_CODE)
                paint.shader = runtimeShader
            } catch (e: Exception) {
                runtimeShader = null
            }
        }
    }

    override fun updateConfig(config: AppShaderConfigEntity) {
        currentConfig = config
    }

    override fun onDraw(canvas: Canvas, width: Float, height: Float, timeSeconds: Float) {
        val config = currentConfig ?: return
        if (!config.isEnabled) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtimeShader != null) {
            val shader = runtimeShader ?: return
            shader.setFloatUniform("uResolution", width, height)
            canvas.drawRect(0f, 0f, width, height, paint)
        }
    }

    override fun release() {
        runtimeShader = null
        paint.shader = null
    }
}
