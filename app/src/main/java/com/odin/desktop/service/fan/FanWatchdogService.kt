package com.odin.desktop.service.fan

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.odin.desktop.OdinDesktopApplication
import com.odin.desktop.R
import com.odin.desktop.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Odin 3 充电与智能风扇后台守护服务。
 * 策略规则：充电中 + 没在玩游戏 + CPU&GPU 温度 <= 60°C -> 自动关闭风扇静音；
 * 若拔掉充电线、进入游戏或温度超过 60°C，立即恢复智能散热。
 */
class FanWatchdogService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var appRepository: AppRepository

    private var isCharging = false
    private var userOriginalFanMode = HardwareController.FAN_SMART
    private var periodicJob: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    isCharging = true
                    evaluateFanPolicy()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    isCharging = false
                    restoreUserFanMode()
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    evaluateFanPolicy()
                }
                AppMonitorAccessibilityService.ACTION_FOREGROUND_CHANGED,
                HardwareController.ACTION_AUTO_FAN_CONFIG_CHANGED -> {
                    evaluateFanPolicy()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as OdinDesktopApplication
        appRepository = AppRepository(this, app.database.tabDao(), app.database.appMappingDao())
        userOriginalFanMode = HardwareController.getFanMode(this)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(AppMonitorAccessibilityService.ACTION_FOREGROUND_CHANGED)
            addAction(HardwareController.ACTION_AUTO_FAN_CONFIG_CHANGED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        startForeground(NOTIFICATION_ID, buildNotification())
        startPeriodicCheck()
    }

    private fun startPeriodicCheck() {
        periodicJob?.cancel()
        periodicJob = serviceScope.launch {
            while (isActive) {
                delay(8000) // 每 8 秒复检一次温度与充电状态
                ensureAccessibilityAlive()
                evaluateFanPolicy()
            }
        }
    }

    private fun ensureAccessibilityAlive() {
        if (!AppMonitorAccessibilityService.isRunning) {
            try {
                val serviceName = "$packageName/${AppMonitorAccessibilityService::class.java.name}"
                val enabled = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                val without = enabled.split(":").filter { it.isNotEmpty() && it != serviceName }.joinToString(":")
                android.provider.Settings.Secure.putString(contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, without)
                val targetList = if (without.isEmpty()) serviceName else "$without:$serviceName"
                android.provider.Settings.Secure.putString(contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, targetList)
                android.provider.Settings.Secure.putString(contentResolver, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            } catch (_: Exception) {}
        }
    }

    private fun evaluateFanPolicy() {
        serviceScope.launch {
            val autoFanEnabled = HardwareController.isAutoFanControlEnabled(this@FanWatchdogService)
            if (!autoFanEnabled) {
                return@launch
            }

            val maxTemp = HardwareController.getMaxCpuGpuTemp()

            // 1. 硬件温度保护：温度高于 60°C 强制开启智能风扇降温
            if (maxTemp > 60f) {
                Log.w(TAG, "Temperature exceeds 60°C ($maxTemp °C), forcing smart fan")
                FanController.setFanMode(this@FanWatchdogService, HardwareController.FAN_SMART)
                return@launch
            }

            // 2. 未充电状态：保持智能风扇或原用户设定
            if (!isCharging) {
                return@launch
            }

            // 3. 判定是否在玩游戏
            val foregroundPackage = AppMonitorAccessibilityService.currentForegroundPackage
            val isPlayingGame = if (foregroundPackage != null && foregroundPackage != packageName) {
                appRepository.isGamePackage(foregroundPackage)
            } else {
                false
            }

            if (isPlayingGame) {
                // 正在玩游戏 -> 开启智能散热
                Log.i(TAG, "Playing game while charging, keeping smart fan")
                if (HardwareController.getFanMode(this@FanWatchdogService) != HardwareController.FAN_SMART) {
                    FanController.setFanMode(this@FanWatchdogService, HardwareController.FAN_SMART)
                }
            } else {
                val perfMode = HardwareController.getPerformanceMode(this@FanWatchdogService)
                if (perfMode != HardwareController.PERF_NORMAL) {
                    // 用户设为中性能或高性能，保持智能散热，不强制静音
                    return@launch
                }
                // 充电 + 正常性能 + 未玩游戏 + 温度 <= 60°C -> 自动关闭风扇静音防噪音
                if (HardwareController.getFanMode(this@FanWatchdogService) != HardwareController.FAN_OFF) {
                    Log.i(TAG, "Charging without game, normal perf, temp <= 60°C ($maxTemp °C), muting fan")
                    FanController.setFanMode(this@FanWatchdogService, HardwareController.FAN_OFF)
                }
            }
        }
    }

    private fun restoreUserFanMode() {
        Log.i(TAG, "Charging disconnected, restoring original fan mode: $userOriginalFanMode")
        FanController.setFanMode(this, userOriginalFanMode)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, OdinDesktopApplication.CHANNEL_FAN)
            .setSmallIcon(R.drawable.ic_tile_afk)
            .setContentTitle(getString(R.string.fan_notification_title))
            .setContentText(getString(R.string.fan_notification_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        periodicJob?.cancel()
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {}
        restoreUserFanMode()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FanWatchdogService"
        private const val NOTIFICATION_ID = 2002
    }
}
