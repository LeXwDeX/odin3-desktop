package com.odin.desktop.service.fan

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.provider.Settings
import android.util.Log
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Odin 3 硬件状态统一管理器。
 * 通过本地特权守护进程 (127.0.0.1:18888) 与系统 Settings 进行零延迟受控双向交互。
 */
object HardwareController {
    private const val TAG = "HardwareController"
    private const val DAEMON_PORT = 18888

    // 1. 性能模式 (0: 正常, 1: 性能, 2: 高性能)
    const val KEY_PERFORMANCE_MODE = "performance_mode"
    const val PERF_NORMAL = 0
    const val PERF_PERFORMANCE = 1
    const val PERF_HIGH_PERFORMANCE = 2

    // 2. 风扇模式 (0: 关, 1: 静音, 2: 智能, 3: 疾风)
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
        return if (maxTemp > 0) maxTemp else 40f
    }

    fun isAutoFanControlEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_FAN_CONTROL, true)
    }

    fun setAutoFanControlEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_FAN_CONTROL, enabled)
            .apply()
        context.sendBroadcast(Intent(ACTION_AUTO_FAN_CONFIG_CHANGED).setPackage(context.packageName))
    }

    /**
     * 发送特权指令至本地 Odin 特权守护进程 (以 Shell 权限写入底层)。
     */
    private fun sendDaemonCommand(command: String): Boolean {
        for (attempt in 0..2) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", DAEMON_PORT), 400)
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    writer.println(command)
                    return true
                }
            } catch (e: Exception) {
                if (attempt < 2) {
                    try {
                        Thread.sleep(80)
                    } catch (_: InterruptedException) {}
                } else {
                    Log.w(TAG, "Daemon connection offline for '$command': ${e.message}")
                }
            }
        }
        return false
    }

    fun forceStopApp(packageName: String): Boolean {
        return sendDaemonCommand("FORCE_STOP $packageName")
    }

    private fun putSystemInt(context: Context, key: String, value: Int): Boolean {
        if (sendDaemonCommand("SET_SYSTEM $key $value")) return true

        try {
            val method = Settings.System::class.java.getMethod(
                "putIntForUser",
                android.content.ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            val result = method.invoke(null, context.contentResolver, key, value, -2) as? Boolean
            if (result == true) return true
        } catch (_: Exception) {}

        return try {
            Settings.System.putInt(context.contentResolver, key, value)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to put system setting $key = $value", e)
            false
        }
    }

    private fun getSystemInt(context: Context, key: String, default: Int): Int {
        try {
            val method = Settings.System::class.java.getMethod(
                "getIntForUser",
                android.content.ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            return method.invoke(null, context.contentResolver, key, default, -2) as Int
        } catch (_: Exception) {}
        return try {
            Settings.System.getInt(context.contentResolver, key, default)
        } catch (_: Exception) {
            default
        }
    }

    // --- CPU 性能模式 ---
    fun getPerformanceMode(context: Context): Int {
        return getSystemInt(context, KEY_PERFORMANCE_MODE, PERF_NORMAL)
    }

    private const val SYSFS_FAN_STATE = "/sys/class/gpio5_pwm2/state"
    private const val SYSFS_FAN_DUTY = "/sys/class/gpio5_pwm2/duty"

    private fun writeSysfs(path: String, value: String): Boolean {
        return try {
            val file = java.io.File(path)
            if (file.exists() && file.canWrite()) {
                file.writeText(value)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write $value to $path", e)
            false
        }
    }

    data class PerfFanResult(val perfMode: Int, val fanMode: Int)

    fun cyclePerformanceMode(context: Context): PerfFanResult {
        val current = getPerformanceMode(context)
        // 循环：正常 -> 性能 -> 高性能 -> 正常
        val next = when (current) {
            PERF_NORMAL -> PERF_PERFORMANCE
            PERF_PERFORMANCE -> PERF_HIGH_PERFORMANCE
            else -> PERF_NORMAL
        }
        setPerformanceMode(context, next)

        // 联动逻辑：当调整性能的时候，风扇也跟着调整，但调整风扇的时候，性能不动
        // 性能：正常 -> 风扇：关闭 (FAN_OFF = 0)
        // 性能：中性能 -> 风扇：智能 (FAN_SMART = 4)
        // 性能：高性能 -> 风扇：智能 (FAN_SMART = 4)
        val linkedFanMode = when (next) {
            PERF_NORMAL -> FAN_OFF
            PERF_PERFORMANCE -> FAN_SMART
            PERF_HIGH_PERFORMANCE -> FAN_SMART
            else -> FAN_SMART
        }
        setFanMode(context, linkedFanMode)

        return PerfFanResult(next, linkedFanMode)
    }

    fun setPerformanceMode(context: Context, mode: Int): Boolean {
        return putSystemInt(context, KEY_PERFORMANCE_MODE, mode)
    }

    // --- 风扇模式 ---
    fun getFanMode(context: Context): Int {
        val mode = getSystemInt(context, KEY_FAN_MODE, FAN_SMART)
        return when (mode) {
            FAN_OFF -> FAN_OFF
            FAN_QUIET -> FAN_QUIET
            FAN_SPORT -> FAN_SPORT
            else -> FAN_SMART
        }
    }

    fun cycleFanMode(context: Context): Int {
        val current = getFanMode(context)
        // 循环：智能 (4) -> 疾风 (5) -> 静音 (1) -> 关闭 (0) -> 智能 (4)
        val next = when (current) {
            FAN_SMART -> FAN_SPORT
            FAN_SPORT -> FAN_QUIET
            FAN_QUIET -> FAN_OFF
            else -> FAN_SMART
        }
        setFanMode(context, next)
        return next
    }

    fun setFanMode(context: Context, mode: Int): Boolean {
        val success = putSystemInt(context, KEY_FAN_MODE, mode)

        // 尝试兜底物理写入 Odin 3 硬件 PWM 节点 (若系统放行)
        try {
            when (mode) {
                FAN_OFF -> {
                    writeSysfs(SYSFS_FAN_DUTY, "0")
                    writeSysfs(SYSFS_FAN_STATE, "0")
                }
                FAN_QUIET -> {
                    writeSysfs(SYSFS_FAN_STATE, "1")
                    writeSysfs(SYSFS_FAN_DUTY, "5000")
                }
                FAN_SMART -> {
                    writeSysfs(SYSFS_FAN_STATE, "1")
                    writeSysfs(SYSFS_FAN_DUTY, "10000")
                }
                FAN_SPORT -> {
                    writeSysfs(SYSFS_FAN_STATE, "1")
                    writeSysfs(SYSFS_FAN_DUTY, "25000")
                }
            }
        } catch (_: Exception) {}

        return success
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
        val current = isJoystickLightEnabled(context)
        val next = !current
        val target = if (next) "1,1" else "0,0"
        
        sendDaemonCommand("SET_SYSTEM $KEY_JOYSTICK_LIGHT_ENABLED $target")
        sendDaemonCommand("SET_SYSTEM $KEY_JOYSTICK_HANDLE_LIGHT_ENABLED $target")
        
        if (Settings.System.canWrite(context)) {
            try {
                Settings.System.putString(context.contentResolver, KEY_JOYSTICK_LIGHT_ENABLED, target)
                Settings.System.putString(context.contentResolver, KEY_JOYSTICK_HANDLE_LIGHT_ENABLED, target)
            } catch (_: Exception) {}
        }
        return next
    }

    // --- 摇杆灯颜色设置 ---
    fun getJoystickColor(context: Context): String {
        return try {
            Settings.System.getString(context.contentResolver, KEY_JOYSTICK_COLOR) ?: "#ff00e5ff,#ff00e5ff"
        } catch (_: Exception) {
            "#ff00e5ff,#ff00e5ff"
        }
    }

    fun setJoystickColor(context: Context, hexColor: String): Boolean {
        val formatted = if (hexColor.contains(",")) hexColor else "$hexColor,$hexColor"
        sendDaemonCommand("SET_SYSTEM $KEY_JOYSTICK_COLOR $formatted")
        if (Settings.System.canWrite(context)) {
            try {
                Settings.System.putString(context.contentResolver, KEY_JOYSTICK_COLOR, formatted)
            } catch (_: Exception) {}
        }
        return true
    }

    // --- 80% 充电限制 ---
    fun isChargeLimit80Enabled(context: Context): Boolean {
        return try {
            Settings.System.getInt(context.contentResolver, KEY_CHARGE_LIMIT_80, 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    fun toggleChargeLimit80(context: Context): Boolean {
        val current = isChargeLimit80Enabled(context)
        val next = if (current) 0 else 1
        
        sendDaemonCommand("SET_SYSTEM $KEY_CHARGE_LIMIT_80 $next")
        sendDaemonCommand("SET_GLOBAL $KEY_CHARGE_LIMIT_80 $next")
        
        try {
            Settings.System.putInt(context.contentResolver, KEY_CHARGE_LIMIT_80, next)
        } catch (_: Exception) {}
        try {
            Settings.Global.putInt(context.contentResolver, KEY_CHARGE_LIMIT_80, next)
        } catch (_: Exception) {}
        return next == 1
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
        val current = isAirplaneModeOn(context)
        val next = !current
        val nextVal = if (next) 1 else 0

        sendDaemonCommand("AIRPLANE $nextVal")
        
        try {
            Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, nextVal)
            val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", next)
            context.sendBroadcast(intent)
        } catch (_: Exception) {}

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
