package com.odin.desktop.shader

import android.app.usage.UsageEvents
import com.odin.desktop.shader.runtime.ForegroundEventTracker
import org.junit.Assert.*
import org.junit.Test

class ForegroundEventTrackerTest {
    private val own = "com.odin.desktop"
    private val game = "com.xiaoji.egggame"
    private fun ForegroundEventTracker.resume(pkg: String, cls: String = "$pkg.MainActivity") =
        accept(UsageEvents.Event.ACTIVITY_RESUMED, pkg, cls)

    @Test fun gameHubHostRemainsTargetThroughTileAndCalibration() {
        val state = ForegroundEventTracker(own)
        state.resume(game, "$game.plugin.pcengine.host.PcEnginePluginHostActivity")
        state.resume("com.android.systemui")
        state.resume(own, "$own.shader.control.ShaderControlActivity")
        assertEquals(game, state.target())
    }
    @Test fun returningHomeDoesNotResurrectRememberedGame() {
        val state = ForegroundEventTracker(own)
        state.resume(game)
        state.resume(own, "$own.ui.MainActivity")
        state.resume(own, "$own.shader.control.ShaderControlActivity")
        assertNull(state.target())
    }
    @Test fun anotherAppSupersedesOldGame() {
        val state = ForegroundEventTracker(own)
        state.resume(game)
        state.resume("com.android.settings")
        assertEquals("com.android.settings", state.target())
    }
    @Test fun screenOffLockAndShutdownDiscardStaleTarget() {
        for (event in listOf(UsageEvents.Event.SCREEN_NON_INTERACTIVE,
            UsageEvents.Event.KEYGUARD_SHOWN, UsageEvents.Event.DEVICE_SHUTDOWN)) {
            val state = ForegroundEventTracker(own)
            state.resume(game)
            state.accept(event, null, null)
            assertNull(state.target())
        }
    }
}
