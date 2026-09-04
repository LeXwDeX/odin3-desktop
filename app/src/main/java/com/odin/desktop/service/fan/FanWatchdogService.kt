package com.odin.desktop.service.fan

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.odin.desktop.OdinDesktopApplication
import com.odin.desktop.R
import com.odin.desktop.data.repository.AppRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/** Charging-mode policy only; disabling it leaves manual fan settings untouched. */
class FanWatchdogService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val policyRequests = Channel<PolicyRequest>(Channel.CONFLATED)
    private val requestVersion = AtomicLong()
    private lateinit var appRepository: AppRepository
    private var receiverRegistered = false
    @Volatile private var isCharging = false
    private var mutedByPolicy = false
    private var failureCount = 0
    private var retryAfterMillis = 0L
    private var nextAccessibilityAttemptMillis = 0L

    private data class PolicyRequest(val version: Long, val resetBackoff: Boolean)
    private class FanModeWriteException(val target: Int, cause: Exception) : Exception("Fan mode write failed", cause)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    isCharging = true
                    requestEvaluation()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    isCharging = false
                    requestEvaluation()
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                    requestEvaluation()
                }
                AppMonitorAccessibilityService.ACTION_FOREGROUND_CHANGED -> requestEvaluation()
                HardwareController.ACTION_AUTO_FAN_CONFIG_CHANGED -> requestEvaluation(resetBackoff = true)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as OdinDesktopApplication
        appRepository = AppRepository(this, app.database.tabDao(), app.database.appMappingDao())
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(AppMonitorAccessibilityService.ACTION_FOREGROUND_CHANGED)
                addAction(HardwareController.ACTION_AUTO_FAN_CONFIG_CHANGED)
            }
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        } catch (error: RuntimeException) {
            Log.e(TAG, "Could not start charging fan service", error)
            stopSelf()
            return
        }
        serviceScope.launch {
            // One consumer prevents periodic and broadcast evaluations from writing concurrently.
            for (request in policyRequests) evaluateFanPolicy(request)
        }
        serviceScope.launch {
            while (isActive) {
                delay(8_000)
                ensureAccessibilityAlive()
                requestEvaluation()
            }
        }
        requestEvaluation()
    }

    private fun requestEvaluation(resetBackoff: Boolean = false) {
        policyRequests.trySend(PolicyRequest(requestVersion.incrementAndGet(), resetBackoff))
    }

    private suspend fun evaluateFanPolicy(request: PolicyRequest) {
        try {
            if (!HardwareController.isAutoFanControlEnabled(this)) {
                mutedByPolicy = false
                failureCount = 0
                retryAfterMillis = 0L
                return
            }
            if (request.resetBackoff) {
                failureCount = 0
                retryAfterMillis = 0L
            }
            if (SystemClock.elapsedRealtime() < retryAfterMillis) return
            val maxTemp = HardwareController.getMaxCpuGpuTemp()
            check(maxTemp.isFinite() && maxTemp > 0f) { "CPU/GPU temperature is unavailable" }
            val foreground = AppMonitorAccessibilityService.currentForegroundPackage
            val currentFan = HardwareController.getFanMode(this)
            val target = when {
                maxTemp > 60f -> safeCoolingMode(currentFan)
                !isCharging -> if (mutedByPolicy) safeCoolingMode(currentFan) else null
                foreground == null -> if (mutedByPolicy) safeCoolingMode(currentFan) else null
                foreground != packageName && appRepository.isGamePackage(foreground) -> safeCoolingMode(currentFan)
                HardwareController.getPerformanceMode(this) != HardwareController.PERF_NORMAL ->
                    if (mutedByPolicy) safeCoolingMode(currentFan) else null
                else -> HardwareController.FAN_OFF
            }
            if (target != null) applyPolicyMode(target, currentFan, request.version)
            failureCount = 0
            retryAfterMillis = 0L
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // A missing sensor or failed game query must never leave our own silent mode trusted.
            if (mutedByPolicy && (error !is FanModeWriteException || error.target == HardwareController.FAN_OFF)) {
                try {
                    val currentFan = HardwareController.getFanMode(this)
                    applyPolicyMode(safeCoolingMode(currentFan), currentFan, request.version)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (recoveryError: Exception) {
                    Log.w(TAG, "Could not restore smart cooling after policy read failure", recoveryError)
                }
            }
            failureCount = (failureCount + 1).coerceAtMost(5)
            val backoff = (30_000L * (1L shl (failureCount - 1))).coerceAtMost(300_000L)
            retryAfterMillis = SystemClock.elapsedRealtime() + backoff
            Log.w(TAG, "Charging fan policy unavailable; retry in ${backoff / 1000}s", error)
        }
    }

    private fun safeCoolingMode(current: Int): Int =
        if (current == HardwareController.FAN_SPORT) current else HardwareController.FAN_SMART

    private suspend fun applyPolicyMode(target: Int, current: Int, version: Long) {
        currentCoroutineContext().ensureActive()
        // Manual selection disables this preference before writing the user's chosen mode.
        if (version != requestVersion.get() || !HardwareController.isAutoFanControlEnabled(this)) return
        if (current != target) {
            // A rejected response may follow a partial write; an OFF attempt still needs recovery.
            if (target == HardwareController.FAN_OFF) mutedByPolicy = true
            try {
                if (!HardwareController.setFanModeIfAutoEnabled(this, target)) {
                    mutedByPolicy = false
                    return
                }
            } catch (error: Exception) {
                throw FanModeWriteException(target, error)
            }
            mutedByPolicy = target == HardwareController.FAN_OFF
        } else if (target != HardwareController.FAN_OFF) {
            mutedByPolicy = false
        }
    }

    private fun ensureAccessibilityAlive() {
        if (AppMonitorAccessibilityService.isRunning) return
        val now = SystemClock.elapsedRealtime()
        if (now < nextAccessibilityAttemptMillis) return
        nextAccessibilityAttemptMillis = now + 60_000
        try {
            val serviceName = "$packageName/${AppMonitorAccessibilityService::class.java.name}"
            val enabled = android.provider.Settings.Secure.getString(contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val without = enabled.split(":").filter { it.isNotEmpty() && it != serviceName }.joinToString(":")
            check(android.provider.Settings.Secure.putString(contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, without))
            val targetList = if (without.isEmpty()) serviceName else "$without:$serviceName"
            check(android.provider.Settings.Secure.putString(contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, targetList))
            check(android.provider.Settings.Secure.putString(contentResolver,
                android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, "1"))
        } catch (error: Exception) {
            Log.w(TAG, "Accessibility restart unavailable; waiting before retry", error)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, OdinDesktopApplication.CHANNEL_FAN)
            .setSmallIcon(R.drawable.ic_tile_afk)
            .setContentTitle(getString(R.string.fan_notification_title))
            .setContentText(getString(R.string.fan_notification_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    override fun onDestroy() {
        requestVersion.incrementAndGet()
        policyRequests.close()
        serviceScope.cancel()
        if (receiverRegistered) {
            try { unregisterReceiver(receiver) } catch (_: RuntimeException) { }
            receiverRegistered = false
        }
        // No startup snapshot is restored: it may predate the user's latest manual setting.
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FanWatchdogService"
        private const val NOTIFICATION_ID = 2002
    }
}
