package com.odin.desktop.shader.model

import org.json.JSONObject
import kotlin.math.abs

enum class ShaderFamily { VULKAN, OPENGL }

enum class ShaderScaling { NONE, NEAREST, LINEAR, FILL, STRETCH, FSR, FSR_ASPECT, DLS, NATURAL }

/** GameNative screen-effect values, using its original percentage and sharpness units. */
data class GameNativeShaderSettings(
    val family: ShaderFamily = ShaderFamily.VULKAN,
    val scaling: ShaderScaling = ShaderScaling.NONE,
    val fsrSharpnessLevel: Int = 3,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val gamma: Float = 1f,
    val enableToon: Boolean = false,
    val enableFXAA: Boolean = false,
    val enableVivid: Boolean = false,
    val enableCRT: Boolean = true,
    val enableNTSC: Boolean = false
) {
    val availableScalingModes: List<ShaderScaling>
        get() = ShaderScaling.entries.filter {
            family == ShaderFamily.VULKAN || (it != ShaderScaling.DLS && it != ShaderScaling.NATURAL)
        }

    fun normalized(): GameNativeShaderSettings = copy(
        scaling = scaling.takeIf { it in availableScalingModes } ?: ShaderScaling.NONE,
        fsrSharpnessLevel = fsrSharpnessLevel.coerceIn(1, 5),
        brightness = brightness.finiteOr(0f).coerceIn(-100f, 100f),
        contrast = contrast.finiteOr(0f).coerceIn(-100f, 100f),
        gamma = gamma.finiteOr(1f).coerceIn(0.5f, 2.5f)
    )

    /** Only Vulkan CRT's multiplicative mask can be drawn without sampling game pixels. */
    val requiresFrameInput: Boolean
        get() {
            val value = normalized()
            val samplesPixels = value.scaling != ShaderScaling.NONE ||
                abs(value.brightness) > 0.001f || abs(value.contrast) > 0.001f ||
                abs(value.gamma - 1f) > 0.001f || value.enableToon || value.enableFXAA ||
                value.enableVivid || value.enableNTSC
            return samplesPixels || (value.family == ShaderFamily.OPENGL && value.enableCRT)
        }

    fun toJson(): String {
        val value = normalized()
        return JSONObject().apply {
            put("version", 1)
            put("family", value.family.name)
            put("scaling", value.scaling.name)
            put("fsrSharpnessLevel", value.fsrSharpnessLevel)
            put("brightness", value.brightness.toDouble())
            put("contrast", value.contrast.toDouble())
            put("gamma", value.gamma.toDouble())
            put("enableToon", value.enableToon)
            put("enableFXAA", value.enableFXAA)
            put("enableVivid", value.enableVivid)
            put("enableCRT", value.enableCRT)
            put("enableNTSC", value.enableNTSC)
        }.toString()
    }

    companion object {
        fun fromJson(json: String): GameNativeShaderSettings {
            if (json.isBlank()) return GameNativeShaderSettings()
            return runCatching {
                val value = JSONObject(json)
                GameNativeShaderSettings(
                    family = ShaderFamily.entries.firstOrNull { it.name == value.optString("family") }
                        ?: ShaderFamily.VULKAN,
                    scaling = ShaderScaling.entries.firstOrNull { it.name == value.optString("scaling") }
                        ?: ShaderScaling.NONE,
                    fsrSharpnessLevel = value.optInt("fsrSharpnessLevel", 3),
                    brightness = value.optDouble("brightness", 0.0).toFloat(),
                    contrast = value.optDouble("contrast", 0.0).toFloat(),
                    gamma = value.optDouble("gamma", 1.0).toFloat(),
                    enableToon = value.optBoolean("enableToon", false),
                    enableFXAA = value.optBoolean("enableFXAA", false),
                    enableVivid = value.optBoolean("enableVivid", false),
                    enableCRT = value.optBoolean("enableCRT", true),
                    enableNTSC = value.optBoolean("enableNTSC", false)
                ).normalized()
            }.getOrDefault(GameNativeShaderSettings())
        }
    }
}

private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback
