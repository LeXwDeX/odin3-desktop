package com.odin.desktop.ui.viewmodel

import com.odin.desktop.R
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.channels.Channel
import com.odin.desktop.service.fan.HardwareController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherHardwareControls(
    private val context: Context,
    private val viewModelScope: CoroutineScope
) {
    private val _performanceMode = MutableStateFlow(-1)
    val performanceMode: StateFlow<Int> = _performanceMode.asStateFlow()

    private val _fanMode = MutableStateFlow(-1)
    val fanMode: StateFlow<Int> = _fanMode.asStateFlow()

    private val _joystickLightEnabled = MutableStateFlow(false)
    val joystickLightEnabled: StateFlow<Boolean> = _joystickLightEnabled.asStateFlow()

    private val _joystickColor = MutableStateFlow("#ff00e5ff")
    val joystickColor: StateFlow<String> = _joystickColor.asStateFlow()

    private val _chargingSeparation = MutableStateFlow(false)
    val chargingSeparation: StateFlow<Boolean> = _chargingSeparation.asStateFlow()

    private val _chargePowerLimit = MutableStateFlow(true)
    val chargePowerLimit: StateFlow<Boolean> = _chargePowerLimit.asStateFlow()

    private val _chargeLimit80 = MutableStateFlow(false)
    val chargeLimit80: StateFlow<Boolean> = _chargeLimit80.asStateFlow()
    private val hardwareLock = Mutex()
    private val hardwareRefreshRequests = Channel<Unit>(Channel.CONFLATED)
    private val hardwareObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { hardwareRefreshRequests.trySend(Unit) }
    }
    private val fanStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == HardwareController.ACTION_FAN_STATE_CHANGED) {
                hardwareRefreshRequests.trySend(Unit)
            }
        }
    }
    private var coolingJob: Job? = null
    @Volatile private var coolingIntentPending = false
    private val coolingIntentRevision = java.util.concurrent.atomic.AtomicLong()
    // Each press updates the selection immediately. Coalesce pending commands by kind,
    // finish in-flight writes, then reconcile only if no newer user intent has arrived.
    private val pendingCoolingActions = linkedMapOf<String, () -> Unit>()
    private var lightJob: Job? = null
    private var colorJob: Job? = null
    private var chargePowerJob: Job? = null
    private var chargeSeparationJob: Job? = null
    private var airplaneJob: Job? = null

    private val _airplaneMode = MutableStateFlow(false)
    val airplaneMode: StateFlow<Boolean> = _airplaneMode.asStateFlow()

    private val _orientationMode = MutableStateFlow(HardwareController.ORIENTATION_LANDSCAPE)
    val orientationMode: StateFlow<Int> = _orientationMode.asStateFlow()

    private val _autoFanControlEnabled = MutableStateFlow(true)
    val autoFanControlEnabled: StateFlow<Boolean> = _autoFanControlEnabled.asStateFlow()

    private val _currentSocTemp = MutableStateFlow(Float.NaN)
    val currentSocTemp: StateFlow<Float> = _currentSocTemp.asStateFlow()

    private val _isDefaultHome = MutableStateFlow(false)
    val isDefaultHome: StateFlow<Boolean> = _isDefaultHome.asStateFlow()

    private val _requestRoleEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestRoleEvent: SharedFlow<Unit> = _requestRoleEvent.asSharedFlow()

    init {
        ContextCompat.registerReceiver(context, fanStateReceiver,
            IntentFilter(HardwareController.ACTION_FAN_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(HardwareController.KEY_FAN_MODE), false, hardwareObserver)
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(HardwareController.KEY_PERFORMANCE_MODE), false, hardwareObserver)
        viewModelScope.launch(Dispatchers.IO) {
            for (ignored in hardwareRefreshRequests) {
                delay(150)
                while (hardwareRefreshRequests.tryReceive().isSuccess) { /* Coalesce OEM notifications. */ }
                // Keep the completion event until the optimistic selection has been committed.
                while (coolingIntentPending) delay(50)
                // Settings observers fire before the OEM's asynchronous PWM write completes.
                // Retry a short, bounded window so an intermediate mismatch is not left on screen.
                for (attempt in 0..2) {
                    hardwareLock.withLock { refreshHardwareStates(refreshPerformance = true) }
                    if (_fanMode.value >= 0) break
                    if (attempt < 2) delay(300)
                }
            }
        }
    }

    fun loadHardwareStates() {
        hardwareRefreshRequests.trySend(Unit)
    }

    fun close() {
        context.unregisterReceiver(fanStateReceiver)
        context.contentResolver.unregisterContentObserver(hardwareObserver)
        hardwareRefreshRequests.close()
    }

    private suspend fun refreshHardwareStates(refreshPerformance: Boolean = false) {
        val revision = coolingIntentRevision.get()
        if (!coolingIntentPending) {
            val performance = if (refreshPerformance || _performanceMode.value < 0) {
                runCatching { HardwareController.getPerformanceMode(context) }.getOrDefault(-1)
            } else _performanceMode.value
            val fan = runCatching { HardwareController.getFanMode(context) }.getOrDefault(-1)
            val auto = HardwareController.isAutoFanControlEnabled(context)
            // Publish on the input thread: checking the revision and changing the visible
            // selection must not race a button press between the check and the assignment.
            withContext(Dispatchers.Main) {
                if (!coolingIntentPending && revision == coolingIntentRevision.get()) {
                    _performanceMode.value = performance
                    _fanMode.value = fan
                    _autoFanControlEnabled.value = auto
                }
            }
        }
        runCatching { HardwareController.isJoystickLightEnabled(context) }.onSuccess { _joystickLightEnabled.value = it }
        runCatching { HardwareController.getJoystickColor(context) }.onSuccess { _joystickColor.value = it.substringBefore(',') }
        runCatching { HardwareController.isChargingSeparationEnabled(context) }.onSuccess { _chargingSeparation.value = it }
        runCatching { HardwareController.isChargePowerLimit5V(context) }.onSuccess { _chargePowerLimit.value = it }
        runCatching { HardwareController.isChargeLimit80Enabled(context) }.onSuccess { _chargeLimit80.value = it }
        runCatching { HardwareController.isAirplaneModeOn(context) }.onSuccess { _airplaneMode.value = it }
        _orientationMode.value = HardwareController.getOrientationMode(context)
        _isDefaultHome.value = HardwareController.isDefaultHome(context)
        _currentSocTemp.value = runCatching { HardwareController.getMaxCpuGpuTemp() }.getOrDefault(Float.NaN)
    }

    private fun changeHardware(refreshPerformance: Boolean = false, action: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            hardwareLock.withLock {
                try {
                    action()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("OdinHardware", "Hardware action failed", failure)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, failure.message ?: context.getString(R.string.text_hardware_setting_failed_try_again), Toast.LENGTH_LONG).show()
                    }
                } finally {
                    refreshHardwareStates(refreshPerformance)
                }
            }
        }
    }

    fun setOrientationMode(mode: Int) {
        _orientationMode.value = mode
        viewModelScope.launch(Dispatchers.IO) {
            HardwareController.setOrientationMode(context, mode)
        }
    }

    fun requestDefaultHome() {
        _requestRoleEvent.tryEmit(Unit)
    }

    fun refreshHomeStatus() {
        _isDefaultHome.value = HardwareController.isDefaultHome(context)
    }

    private fun enqueueCoolingAction(kind: String, action: () -> Unit) {
        coolingIntentRevision.incrementAndGet()
        coolingIntentPending = true
        pendingCoolingActions.remove(kind)
        pendingCoolingActions[kind] = action
        if (coolingJob?.isActive == true) return
        coolingJob = viewModelScope.launch {
            try {
                delay(150)
                while (pendingCoolingActions.isNotEmpty()) {
                    val actions = pendingCoolingActions.values.toList()
                    pendingCoolingActions.clear()
                    withContext(Dispatchers.IO) {
                        hardwareLock.withLock {
                            for (pending in actions) {
                                try {
                                    pending()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    android.util.Log.w("OdinHardware", "Performance/fan action failed", failure)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, failure.message ?: context.getString(R.string.text_performance_or_fan_setting_failed_try_again), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                    if (pendingCoolingActions.isEmpty()) {
                        coolingIntentPending = false
                        withContext(Dispatchers.IO) {
                            hardwareLock.withLock { refreshHardwareStates(refreshPerformance = true) }
                        }
                    }
                    if (pendingCoolingActions.isNotEmpty()) delay(150)
                }
            } finally {
                coolingIntentPending = false
            }
        }
    }

    fun cyclePerformanceMode() {
        val current = _performanceMode.value
        val next = if (current in 0..2) (current + 1) % 3 else HardwareController.PERF_NORMAL
        _performanceMode.value = next
        val auto = _autoFanControlEnabled.value
        val currentFan = _fanMode.value
        val fanTarget = if (!auto && currentFan == HardwareController.FAN_SPORT) HardwareController.FAN_SPORT
            else if (auto || next != HardwareController.PERF_NORMAL) HardwareController.FAN_SMART
            else HardwareController.FAN_OFF
        _fanMode.value = fanTarget
        enqueueCoolingAction("performance") { HardwareController.setPerformanceAndFan(context, next, fanTarget) }
    }

    fun cycleFanMode() {
        val targetFan = when (_fanMode.value) {
            HardwareController.FAN_OFF -> HardwareController.FAN_SMART
            HardwareController.FAN_SMART -> HardwareController.FAN_SPORT
            HardwareController.FAN_SPORT -> HardwareController.FAN_OFF
            else -> HardwareController.FAN_SMART
        }
        _fanMode.value = targetFan
        _autoFanControlEnabled.value = false
        // Manual fan selection includes disabling automation, superseding an earlier toggle.
        pendingCoolingActions.remove("automation")
        enqueueCoolingAction("fan") {
            HardwareController.setManualFanMode(context, targetFan)
        }
    }

    fun toggleAutoFanControl() {
        val next = !(_autoFanControlEnabled.value)
        _autoFanControlEnabled.value = next
        enqueueCoolingAction("automation") { HardwareController.setAutoFanControlEnabled(context, next) }
    }

    fun toggleJoystickLight() {
        val next = !_joystickLightEnabled.value
        _joystickLightEnabled.value = next

        lightJob?.cancel()
        lightJob = viewModelScope.launch(Dispatchers.IO) {
            delay(150)
            hardwareLock.withLock {
                try {
                    HardwareController.setJoystickLightEnabled(context, next)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("OdinHardware", "Joystick light toggle failed", failure)
                    runCatching { HardwareController.isJoystickLightEnabled(context) }
                        .onSuccess { _joystickLightEnabled.value = it }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, failure.message ?: context.getString(R.string.text_stick_lighting_could_not_be_changed_try), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun toggleChargingSeparation() {
        val next = !_chargingSeparation.value
        _chargingSeparation.value = next

        chargeSeparationJob?.cancel()
        chargeSeparationJob = viewModelScope.launch(Dispatchers.IO) {
            delay(150)
            hardwareLock.withLock {
                try {
                    HardwareController.setChargingSeparationEnabled(context, next)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("OdinHardware", "Charging separation toggle failed", failure)
                    runCatching { HardwareController.isChargingSeparationEnabled(context) }
                        .onSuccess { _chargingSeparation.value = it }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, failure.message ?: context.getString(R.string.text_bypass_charging_could_not_be_changed_try), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun toggleChargePowerLimit() {
        val next = !_chargePowerLimit.value
        _chargePowerLimit.value = next

        chargePowerJob?.cancel()
        chargePowerJob = viewModelScope.launch(Dispatchers.IO) {
            delay(150)
            hardwareLock.withLock {
                try {
                    HardwareController.setChargePowerLimit5V(context, next)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("OdinHardware", "Power limit toggle failed", failure)
                    runCatching { HardwareController.isChargePowerLimit5V(context) }
                        .onSuccess { _chargePowerLimit.value = it }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, failure.message ?: context.getString(R.string.text_charging_power_could_not_be_changed_try), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun toggleAirplaneMode() {
        val next = !_airplaneMode.value
        _airplaneMode.value = next

        airplaneJob?.cancel()
        airplaneJob = viewModelScope.launch(Dispatchers.IO) {
            delay(150)
            hardwareLock.withLock {
                try {
                    HardwareController.setAirplaneMode(context, next)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("OdinHardware", "Airplane mode toggle failed", failure)
                    runCatching { HardwareController.isAirplaneModeOn(context) }
                        .onSuccess { _airplaneMode.value = it }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, failure.message ?: context.getString(R.string.text_airplane_mode_could_not_be_changed_try), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun refreshSocTemp() {
        viewModelScope.launch(Dispatchers.IO) {
            _currentSocTemp.value = runCatching { HardwareController.getMaxCpuGpuTemp() }.getOrDefault(Float.NaN)
        }
    }

    fun setJoystickColor(hex: String) {
        _joystickColor.value = hex
        colorJob?.cancel()
        colorJob = viewModelScope.launch(Dispatchers.IO) {
            delay(150)
            hardwareLock.withLock {
                try {
                    HardwareController.setJoystickColor(context, hex)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("OdinHardware", "Joystick color set failed", failure)
                    runCatching { HardwareController.getJoystickColor(context) }
                        .onSuccess { _joystickColor.value = it.substringBefore(',') }
                }
            }
        }
    }

}
