package com.odin.desktop.shader

import com.odin.desktop.shader.model.GameNativeShaderSettings
import com.odin.desktop.shader.model.ShaderFamily
import com.odin.desktop.shader.model.ShaderScaling
import com.odin.desktop.shader.runtime.ShaderStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ShaderStatusTest {
    private val crt = GameNativeShaderSettings()
    private fun status(effects: GameNativeShaderSettings = crt, enabled: Boolean = true,
                       sdk: Int = 35, permission: Boolean = true, known: Boolean = true,
                       paused: Boolean = false) = ShaderStatus.evaluate(enabled, effects, sdk, permission, known, paused)

    @Test fun enabledPreferenceNeverMeansApplied() {
        assertEquals(ShaderStatus.CHECKING, status())
        assertEquals(ShaderStatus.DISABLED, status(enabled = false))
        assertEquals(ShaderStatus.PAUSED, status(paused = true))
        assertEquals(ShaderStatus.UNKNOWN, status(known = false))
    }

    @Test fun everyPixelSamplingCombinationIsPreviewOnly() {
        val unsupported = listOf(crt.copy(enableFXAA = true), crt.copy(enableToon = true),
            crt.copy(enableVivid = true), crt.copy(enableNTSC = true), crt.copy(brightness = 1f),
            crt.copy(contrast = 1f), crt.copy(gamma = 1.05f), crt.copy(family = ShaderFamily.OPENGL)) +
            ShaderScaling.entries.filter { it != ShaderScaling.NONE }.map { crt.copy(scaling = it) }
        unsupported.forEach {
            assertEquals(it.toString(), ShaderStatus.PREVIEW_ONLY, status(it))
            assertEquals(ShaderStatus.PREVIEW_ONLY, status(it, permission = false, paused = true))
        }
    }

    @Test fun missingCapabilityAndNoEffectAreDistinct() {
        assertEquals(ShaderStatus.NO_EFFECT, status(crt.copy(enableCRT = false)))
        assertEquals(ShaderStatus.UNSUPPORTED_SYSTEM, status(sdk = 32))
        assertEquals(ShaderStatus.PERMISSION_REQUIRED, status(permission = false))
        assertEquals(ShaderStatus.UNKNOWN, status(known = false, paused = true))
        assertEquals(ShaderStatus.DISABLED, status(crt.copy(enableFXAA = true), enabled = false))
    }
}
