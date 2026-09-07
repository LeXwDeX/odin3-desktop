package com.odin.desktop.service.fan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.odin.desktop.OdinDesktopApplication
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32, 35], application = OdinDesktopApplication::class)
class FanFeatureRemovalTest {
    @Test fun upgradeRetiresOldPreferenceAndChannelWithoutChangingFanOrOtherPreferences() {
        val app = RuntimeEnvironment.getApplication() as OdinDesktopApplication
        val prefs = app.getSharedPreferences(HardwareController.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_fan_control_enabled", true)
            .putInt("orientation_mode", 1).putString("unrelated_user_setting", "keep").commit()
        Settings.System.putInt(app.contentResolver, "fan_mode", 0)
        val notifications = app.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(
            "odin_channel_fan", "Old monitor", NotificationManager.IMPORTANCE_DEFAULT))
        // Re-enter the real application startup with legacy data, including enabled automation.
        app.onTerminate()
        app.onCreate()
        assertFalse(prefs.contains("auto_fan_control_enabled"))
        assertEquals(1, prefs.getInt("orientation_mode", -1))
        assertEquals("keep", prefs.getString("unrelated_user_setting", null))
        assertEquals(0, Settings.System.getInt(app.contentResolver, "fan_mode", -1))
        assertNull(notifications.getNotificationChannel("odin_channel_fan"))
        assertNotNull(notifications.getNotificationChannel(OdinDesktopApplication.CHANNEL_AFK))
        assertNull(shadowOf(app).nextStartedService)
        app.onTerminate()
        app.onCreate()
        assertNull(shadowOf(app).nextStartedService)
        assertEquals(0, Settings.System.getInt(app.contentResolver, "fan_mode", -1))
    }

    @Test fun manifestHasNoFanMonitorAccessibilityOrBootEntryPoint() {
        val app = RuntimeEnvironment.getApplication()
        val info = app.packageManager.getPackageInfo(app.packageName,
            PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PERMISSIONS)
        assertFalse(info.services.orEmpty().any { it.name.contains(".service.fan.") })
        assertFalse(info.receivers.orEmpty().any { it.name.endsWith("BootCompletedReceiver") })
        assertFalse(info.requestedPermissions.orEmpty().contains("android.permission.RECEIVE_BOOT_COMPLETED"))
        assertTrue(info.services.orEmpty().any { it.name.endsWith("AfkOverlayService") })
        assertTrue(info.services.orEmpty().any { it.name.endsWith("AfkTileService") })
    }
}
