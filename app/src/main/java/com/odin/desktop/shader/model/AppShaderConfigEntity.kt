package com.odin.desktop.shader.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 分应用 VideoShader 着色器配置实体
 * 允许针对特定模拟器或游戏单独开启/关闭着色器，并定制扫描线、动态效果与复古滤镜参数
 */
@Entity(tableName = "app_shader_configs")
data class AppShaderConfigEntity(
    @PrimaryKey
    val packageName: String,
    val isEnabled: Boolean = false,
    val presetId: String = PRESET_CRT_SCANLINE_STATIC,
    val isDynamic: Boolean = false,         // 静态 or 动态扫描线 (时间动画)
    val scanlineIntensity: Float = 0.45f,   // 扫描线暗度 (0.0 ~ 1.0)
    val phosphorIntensity: Float = 0.20f,   // RGB 荧光格强度 (0.0 ~ 1.0)
    val vignetteIntensity: Float = 0.30f,   // 边缘暗角弧度 (0.0 ~ 1.0)
    val animationSpeed: Float = 1.0f        // 动态扫描线蠕动速度 (0.5 ~ 3.0)
) {
    companion object {
        const val PRESET_CRT_SCANLINE_STATIC = "crt_scanline_static"
        const val PRESET_CRT_SCANLINE_DYNAMIC = "crt_scanline_dynamic"
        const val PRESET_PVM_RGB = "pvm_rgb_mask"
        const val PRESET_RETRO_LCD = "retro_lcd_grid"
        const val PRESET_VIGNETTE = "vignette_crt"

        fun defaultFor(packageName: String): AppShaderConfigEntity {
            return AppShaderConfigEntity(
                packageName = packageName,
                isEnabled = false,
                presetId = PRESET_CRT_SCANLINE_STATIC,
                isDynamic = false,
                scanlineIntensity = 0.45f,
                phosphorIntensity = 0.20f,
                vignetteIntensity = 0.30f,
                animationSpeed = 1.0f
            )
        }
    }
}
