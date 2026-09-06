package com.odin.desktop.shader

import com.odin.desktop.shader.model.GameNativeShaderSettings
import com.odin.desktop.shader.model.ShaderScaling
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShaderLegacyConfigTest {
    @Test fun removedFsrValuesDisableOnlyTheirScaling() {
        for (old in listOf("FSR", "FSR_ASPECT")) {
            val settings = GameNativeShaderSettings.fromJson("""{"scaling":"$old","fsrSharpnessLevel":4,"brightness":24,"contrast":-6,"gamma":1.2,"enableCRT":false,"enableFXAA":true}""")
            assertEquals(ShaderScaling.NONE, settings.scaling)
            assertEquals(24f, settings.brightness)
            assertEquals(-6f, settings.contrast)
            assertEquals(1.2f, settings.gamma)
            assertFalse(settings.enableCRT)
            assertTrue(settings.enableFXAA)
            assertFalse(settings.toJson().contains("FSR"))
            assertFalse(settings.toJson().contains("fsrSharpnessLevel"))
            assertEquals(settings, GameNativeShaderSettings.fromJson(settings.toJson()))
        }
    }

    @Test fun existingDlsSharpnessSurvivesFieldRename() {
        val settings = GameNativeShaderSettings.fromJson("""{"scaling":"DLS","fsrSharpnessLevel":5}""")
        assertEquals(ShaderScaling.DLS, settings.scaling)
        assertEquals(5, settings.sharpnessLevel)
        assertEquals(settings, GameNativeShaderSettings.fromJson(settings.toJson()))
    }
}
