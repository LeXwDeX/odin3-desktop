package com.odin.desktop.shader.pipeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import com.odin.desktop.shader.model.AppShaderConfigEntity

class AgslVideoShaderPipeline : IVideoShaderPipeline {

    override val id: String = "agsl_builtin_pipeline"
    override val displayName: String = "AGSL 硬件加速嵌入式管线 (Android 13+)"

    private var runtimeShader: RuntimeShader? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var currentConfig: AppShaderConfigEntity? = null

    companion object {
        // 核心算法移植自 GameNative 项目 (com.winlator.renderer.effects.CRTEffect 与 window.frag)
        private const val AGSL_SHADER_CODE = """
            uniform float2 uResolution;
            uniform float uTime;
            uniform float uScanlineIntensity;
            uniform float uIsDynamic;
            uniform float uPhosphorIntensity;
            uniform float uVignetteIntensity;
            uniform float uAnimationSpeed;

            vec4 main(vec2 fragCoord) {
                vec2 uv = fragCoord / uResolution;

                // GameNative 标准扫描线基准尺寸 1024.0
                float scanlineSize = 1024.0;
                float timeOffset = uIsDynamic * uTime * uAnimationSpeed * 10.0;

                // 参照 GameNative CRTEffect.java:
                // float scanlineX = abs(sin(vUV.x * SCANLINE_SIZE) * 0.5 * SCANLINE_INTENSITY_X);
                // float scanlineY = abs(sin(vUV.y * SCANLINE_SIZE) * 0.5 * SCANLINE_INTENSITY_Y);
                float intensityX = 0.125 * (uPhosphorIntensity * 2.5 + 0.2);
                float intensityY = 0.375 * (uScanlineIntensity * 2.2);

                float scanlineX = abs(sin(uv.x * scanlineSize) * 0.5 * intensityX);
                float scanlineY = abs(sin(uv.y * scanlineSize + timeOffset) * 0.5 * intensityY);
                float darkFactor = (scanlineX + scanlineY) * 1.6;

                // CRT 微弱光栅亮条 (保证暗色 UI 背景下也能呈现清晰细致的扫描线物理质感)
                float rasterGlow = pow(abs(cos(uv.y * scanlineSize + timeOffset)), 2.5) * 0.08 * uScanlineIntensity;

                // 边缘暗角衰减 (GameNative 风格)
                float vignette = 0.0;
                if (uVignetteIntensity > 0.01) {
                    vec2 vCoord = (uv - 0.5) * 2.0;
                    vignette = dot(vCoord, vCoord) * (0.16 * uVignetteIntensity);
                }

                // 遮罩合成
                float alpha = clamp(darkFactor + vignette, 0.0, 0.90);
                vec3 beamColor = vec3(rasterGlow);

                return vec4(beamColor, alpha);
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
            shader.setFloatUniform("uTime", timeSeconds)
            shader.setFloatUniform("uScanlineIntensity", config.scanlineIntensity.coerceIn(0.0f, 1.0f))
            shader.setFloatUniform("uIsDynamic", if (config.isDynamic) 1.0f else 0.0f)
            shader.setFloatUniform("uPhosphorIntensity", config.phosphorIntensity.coerceIn(0.0f, 1.0f))
            shader.setFloatUniform("uVignetteIntensity", config.vignetteIntensity.coerceIn(0.0f, 1.0f))
            shader.setFloatUniform("uAnimationSpeed", config.animationSpeed.coerceIn(0.1f, 5.0f))

            canvas.drawRect(0f, 0f, width, height, paint)
        } else {
            // 低版本降级：直接通过 Canvas 简单绘制等距扫描线
            val linePaint = Paint().apply {
                color = Color.BLACK
                alpha = (config.scanlineIntensity * 120).toInt().coerceIn(0, 255)
                strokeWidth = 2f
            }
            var y = 0f
            while (y < height) {
                canvas.drawLine(0f, y, width, y, linePaint)
                y += 4f
            }
        }
    }

    override fun release() {
        runtimeShader = null
        paint.shader = null
    }
}
