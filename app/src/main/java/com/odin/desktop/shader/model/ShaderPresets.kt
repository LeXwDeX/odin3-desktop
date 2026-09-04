package com.odin.desktop.shader.model

import androidx.annotation.StringRes
import com.odin.desktop.R

data class ShaderPreset(
    val id: String,
    @StringRes val label: Int,
    private val settings: GameNativeShaderSettings
) {
    fun settings(family: ShaderFamily): GameNativeShaderSettings = settings.copy(family = family)
}

/** Built-in preset descriptors; IDs stay independent of language and display order. */
object ShaderPresets {
    private val neutral = GameNativeShaderSettings(enableCRT = false)
    val builtIn = listOf(
        ShaderPreset("gamenative.trinitron", R.string.text_trinitron_crt, neutral.copy(enableCRT = true, contrast = 10f)),
        ShaderPreset("gamenative.arcade", R.string.text_retro_arcade, neutral.copy(enableCRT = true, enableVivid = true, brightness = 5f, contrast = 15f, gamma = 0.95f)),
        ShaderPreset("gamenative.vivid", R.string.text_vivid_game, neutral.copy(enableVivid = true, contrast = 10f)),
        ShaderPreset("gamenative.fxaa", R.string.text_hd_fxaa, neutral.copy(enableFXAA = true)),
        ShaderPreset("gamenative.original", R.string.text_unfiltered, neutral)
    )

    fun indexOf(settings: GameNativeShaderSettings): Int =
        builtIn.indexOfFirst { it.settings(settings.family).normalized() == settings.normalized() }
}
