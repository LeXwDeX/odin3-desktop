package com.odin.desktop

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.odin.desktop.data.db.OdinDatabase
import com.odin.desktop.service.fan.FanWatchdogService

class OdinDesktopApplication : Application() {

    val database: OdinDatabase by lazy { OdinDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val afkChannel = NotificationChannel(
                CHANNEL_AFK,
                "息屏挂机保护",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "息屏挂机防烧屏前台通知"
                setShowBadge(false)
            }

            val fanChannel = NotificationChannel(
                CHANNEL_FAN,
                "充电风扇守护",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "充电与智能散热状态守护通知"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(afkChannel)
            notificationManager.createNotificationChannel(fanChannel)
        }
    }

    private fun startBackgroundServices() {
        // 自动拉起充电风扇守护服务
        val fanIntent = Intent(this, FanWatchdogService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(fanIntent)
        } else {
            startService(fanIntent)
        }
    }

    companion object {
        const val CHANNEL_AFK = "odin_channel_afk"
        const val CHANNEL_FAN = "odin_channel_fan"
    }
}
