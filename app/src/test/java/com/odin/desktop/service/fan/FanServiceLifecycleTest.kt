package com.odin.desktop.service.fan

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FanServiceLifecycleTest {
    @Test fun manualModeStopsServiceWithoutStartingIt() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences(HardwareController.PREFS_NAME, 0).edit()
            .putBoolean(HardwareController.KEY_AUTO_FAN_CONTROL, false).commit()
        FanWatchdogService.sync(app)
        assertNull(shadowOf(app).nextStartedService)
        assertEquals(FanWatchdogService::class.java.name, shadowOf(app).nextStoppedService.component?.className)
    }

    @Test fun enabledAutomationStartsOnlyItsMonitor() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences(HardwareController.PREFS_NAME, 0).edit()
            .putBoolean(HardwareController.KEY_AUTO_FAN_CONTROL, true).commit()
        FanWatchdogService.sync(app)
        assertEquals(FanWatchdogService::class.java.name, shadowOf(app).nextStartedService.component?.className)
        assertNull(shadowOf(app).nextStoppedService)
    }
}
