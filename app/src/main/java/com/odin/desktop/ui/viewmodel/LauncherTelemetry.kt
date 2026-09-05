package com.odin.desktop.ui.viewmodel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.odin.desktop.service.fan.HardwareController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

data class BatteryReading(val percent: Int? = null, val status: Int = BatteryManager.BATTERY_STATUS_UNKNOWN)
data class LauncherTelemetry(
    val battery: BatteryReading = BatteryReading(),
    val fan: HardwareController.FanTelemetry? = null
)

/** Read-only sampling exists only while the launcher is STARTED. Unknown is never zero. */
class LauncherTelemetryRepository(private val context: Context) {
    fun observe(): Flow<LauncherTelemetry> = combine(battery(), fan()) { battery, fan ->
        LauncherTelemetry(battery, fan)
    }

    private fun battery(): Flow<BatteryReading> = callbackFlow {
        fun sendReading(intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            trySend(BatteryReading(
                if (scale > 0 && level in 0..scale) (level.toLong() * 100 / scale).toInt() else null,
                status
            ))
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { sendReading(intent) }
        }
        sendReading(context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    private fun fan(): Flow<HardwareController.FanTelemetry?> = flow {
        emit(null)
        while (currentCoroutineContext().isActive) {
            val sample = try { HardwareController.getFanTelemetry(context) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            emit(sample)
            delay(2_000)
        }
    }.flowOn(Dispatchers.IO).distinctUntilChanged()
}
