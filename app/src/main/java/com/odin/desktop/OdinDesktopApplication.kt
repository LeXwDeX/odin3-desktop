package com.odin.desktop

import com.odin.desktop.R
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.odin.desktop.data.db.OdinDatabase

class OdinDesktopApplication : Application() {

    val database: OdinDatabase by lazy { OdinDatabase.getDatabase(this) }

    val appRepository by lazy { com.odin.desktop.data.repository.AppRepository(this, database) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val afkChannel = NotificationChannel(
                CHANNEL_AFK,
                getString(R.string.text_idle_screen_protection),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.text_foreground_notification_for_oled_idle_protection)
                setShowBadge(false)
            }

            val fanChannel = NotificationChannel(
                CHANNEL_FAN,
                getString(R.string.text_charging_fan_monitor),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.text_charging_and_cooling_status_notifications)
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(afkChannel)
            notificationManager.createNotificationChannel(fanChannel)
        }
    }

    companion object {
        const val CHANNEL_AFK = "odin_channel_afk"
        const val CHANNEL_FAN = "odin_channel_fan"
    }
}
