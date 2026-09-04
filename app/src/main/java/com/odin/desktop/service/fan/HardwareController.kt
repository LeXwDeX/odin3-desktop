package com.odin.desktop.service.fan

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.provider.Settings
import com.odin.desktop.hardware.HardwareBridgeClient
import com.odin.desktop.hardware.HardwareControlException

/**
 * Odin 3 硬件状态统一管理器。
 * 写入由认证的 ADB Shell 服务执行并读回；调用方必须在 IO 线程执行硬件操作。
 */
object HardwareController {

    // 1. 性能模式 (0: 正常, 1: 性能, 2: 高性能)
    const val KEY_PERFORMANCE_MODE = "performance_mode"
    const val PERF_NORMAL = 0
    const val PERF_PERFORMANCE = 1
    const val PERF_HIGH_PERFORMANCE = 2

    // 2. Odin 3 风扇模式 (0: 关, 1: 静音, 4: 智能, 5: 最高固定档)
    const val KEY_FAN_MODE = "fan_mode"
    const val FAN_OFF = 0
    const val FAN_QUIET = 1
    const val FAN_SMART = 4
    const val FAN_SPORT = 5

    // 3. 摇杆灯 ("0,0" / "1,1")
    const val KEY_JOYSTICK_LIGHT_ENABLED = "joystick_light_enabled"
    const val KEY_JOYSTICK_HANDLE_LIGHT_ENABLED = "joystick_handle_light_enabled"
    const val KEY_JOYSTICK_COLOR = "joystick_led_light_picker_color"

    // 4. 充电限制 80% (0 / 1)
    const val KEY_CHARGE_LIMIT_80 = "percent_80_charge_limit"
    const val KEY_CHARGE_POWER_LIMIT = "charging_limit_power_limit"

    // 5. 屏幕方向模式 (仅保留固定横屏与传感器横屏)
    const val ORIENTATION_LANDSCAPE = 0
    const val ORIENTATION_SENSOR_LANDSCAPE = 1

    // 6. 自动风扇调度配置
    const val PREFS_NAME = "odin_desktop_prefs"
    const val KEY_AUTO_FAN_CONTROL = "auto_fan_control_enabled"
    const val ACTION_AUTO_FAN_CONFIG_CHANGED = "com.odin.desktop.action.AUTO_FAN_CONFIG_CHANGED"

    /**
     * 读取 Odin 3 硬件 SoC (CPU 与 GPU) 实时最高温度 (°C)
     */
    fun getMaxCpuGpuTemp(): Float {
        var maxTemp = 0f
        try {
            val dir = java.io.File("/sys/class/thermal")
            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith("thermal_zone")) {
                    val typeFile = java.io.File(file, "type")
                    if (typeFile.exists()) {
                        val type = typeFile.readText().trim()
                        if (type.contains("cpu", ignoreCase = true) || type.contains("gpu", ignoreCase = true)) {
                            val tempFile = java.io.File(file, "temp")
                            if (tempFile.exists()) {
                                val raw = tempFile.readText().trim().toLongOrNull() ?: 0L
                                val temp = if (raw > 1000) raw / 1000f else raw.toFloat()
                                if (temp in 10f..120f && temp > maxTemp) {
                                    maxTemp = temp
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return if (maxTemp > 0) maxTemp else Float.NaN
    }

    fun isAutoFanControlEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_FAN_CONTROL, true)
    }

    @Synchronized
    fun setAutoFanControlEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_FAN_CONTROL, enabled)
            .apply()
        context.sendBroadcast(Intent(ACTION_AUTO_FAN_CONFIG_CHANGED).setPackage(context.packageName))
    }

    private fun systemValue(context: Context, key: String): String? =
        Settings.System.getString(context.contentResolver, key)

    private fun setSystem(context: Context, key: String, value: String) {
        val reply = HardwareBridgeClient.request(context, "SET\t$key\t$value")
        if (reply != listOf(key, value) || systemValue(context, key) != value) {
            throw HardwareControlException("硬件设置读回不一致，请刷新状态后重试。")
        }
    }

    // --- CPU 性能模式 ---
    fun getPerformanceMode(context: Context): Int {
        val reply = HardwareBridgeClient.request(context, "PERFORMANCE_GET")
        val value = reply.getOrNull(1)?.toIntOrNull()
        if (reply.size != 2 || reply[0] != "PERFORMANCE" || value == null || value !in 0..2) {
            throw HardwareControlException("系统性能模式不可用，请刷新状态。")
        }
        return value
    }

    data class PerfFanResult(val perfMode: Int, val fanMode: Int)

    fun cyclePerformanceMode(context: Context): PerfFanResult {
        val next = (getPerformanceMode(context) + 1) % 3
        setPerformanceMode(context, next)
        if (next != PERF_NORMAL) setFanMode(context, FAN_SMART)
        return PerfFanResult(getPerformanceMode(context), getFanMode(context))
    }

    fun setPerformanceMode(context: Context, mode: Int): Boolean {
        require(mode in 0..2)
        val reply = HardwareBridgeClient.request(context, "PERFORMANCE\t$mode")
        if (reply != listOf("PERFORMANCE", mode.toString()) || getPerformanceMode(context) != mode) {
            throw HardwareControlException("系统未切换到所选性能模式，请刷新状态。")
        }
        return true
    }

    // --- 风扇模式 ---
    fun getFanMode(context: Context): Int = systemValue(context, KEY_FAN_MODE)?.toIntOrNull()
        ?: throw HardwareControlException("无法读取风扇模式。")

    fun cycleFanMode(context: Context): Int {
        val next = when (getFanMode(context)) {
            FAN_OFF -> FAN_SMART
            FAN_SMART -> FAN_SPORT
            else -> FAN_OFF
        }
        setFanMode(context, next)
        return getFanMode(context)
    }

    fun setFanMode(context: Context, mode: Int): Boolean {
        require(mode == FAN_OFF || mode == FAN_SMART || mode == FAN_SPORT)
        setSystem(context, KEY_FAN_MODE, mode.toString())
        return true
    }

    /** Serialize the last policy check with disabling automation before a manual fan write. */
    @Synchronized
    fun setFanModeIfAutoEnabled(context: Context, mode: Int): Boolean {
        if (!isAutoFanControlEnabled(context)) return false
        return setFanMode(context, mode)
    }

    // --- 摇杆灯开关 ---
    fun isJoystickLightEnabled(context: Context): Boolean {
        return try {
            val value = Settings.System.getString(context.contentResolver, KEY_JOYSTICK_LIGHT_ENABLED) ?: "0,0"
            value.startsWith("1")
        } catch (_: Exception) {
            false
        }
    }

    fun toggleJoystickLight(context: Context): Boolean {
        val next = !isJoystickLightEnabled(context)
        val value = if (next) "1,1" else "0,0"
        val reply = HardwareBridgeClient.request(context, "LIGHTS\t$value")
        if (reply != listOf("LIGHTS", value) ||
            systemValue(context, KEY_JOYSTICK_LIGHT_ENABLED) != value ||
            systemValue(context, KEY_JOYSTICK_HANDLE_LIGHT_ENABLED) != value) {
            throw HardwareControlException("摇杆灯状态读回不一致，请刷新状态。")
        }
        return next
    }

    fun getJoystickColor(context: Context): String =
        systemValue(context, KEY_JOYSTICK_COLOR) ?: "#ff00e5ff,#ff00e5ff"

    fun setJoystickColor(context: Context, hexColor: String): Boolean {
        val value = if (',' in hexColor) hexColor else "$hexColor,$hexColor"
        require(value.matches(Regex("#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?,#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?")))
        setSystem(context, KEY_JOYSTICK_COLOR, value)
        return true
    }

    fun isChargeLimit80Enabled(context: Context): Boolean = systemValue(context, KEY_CHARGE_LIMIT_80) == "1"
    fun isChargePowerLimitEnabled(context: Context): Boolean = systemValue(context, KEY_CHARGE_POWER_LIMIT) == "1"

    fun toggleChargeLimit80(context: Context): Boolean {
        val next = !(isChargeLimit80Enabled(context) && isChargePowerLimitEnabled(context))
        val value = if (next) "1" else "0"
        val reply = HardwareBridgeClient.request(context, "CHARGE\t$value")
        if (reply != listOf("CHARGE", value) ||
            isChargeLimit80Enabled(context) != next || isChargePowerLimitEnabled(context) != next) {
            throw HardwareControlException("充电限制未全部切换，请刷新两项状态。")
        }
        return next
    }

    // --- 飞行模式 ---
    fun isAirplaneModeOn(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (_: Exception) {
            false
        }
    }

    fun toggleAirplaneMode(context: Context): Boolean {
        val next = !isAirplaneModeOn(context)
        try {
            check(Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, if (next) 1 else 0))
            context.sendBroadcast(Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", next))
        } catch (error: Exception) {
            throw HardwareControlException("飞行模式切换失败，请刷新状态。", error)
        }
        if (isAirplaneModeOn(context) != next) throw HardwareControlException("飞行模式读回不一致。")
        return next
    }

    // --- 屏幕方向规则 ---
    fun applyOrientation(activity: Activity, mode: Int) {
        activity.requestedOrientation = when (mode) {
            ORIENTATION_SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }
}
