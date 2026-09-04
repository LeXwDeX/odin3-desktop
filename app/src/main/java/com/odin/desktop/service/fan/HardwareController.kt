package com.odin.desktop.service.fan

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
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

    // 4. 充电限制与充电分离
    const val KEY_CHARGE_LIMIT_80 = "percent_80_charge_limit"
    const val KEY_CHARGE_POWER_LIMIT = "charging_limit_power_limit"
    const val KEY_CHARGING_SEPARATION = "is_charging_separation"

    // 5. 屏幕方向模式 (仅保留固定横屏与传感器横屏)
    const val ORIENTATION_LANDSCAPE = 0
    const val ORIENTATION_SENSOR_LANDSCAPE = 1
    const val KEY_ORIENTATION_MODE = "orientation_mode"
    const val SYSTEM_KEY_FORCE_LANDSCAPE = "force_landscape"

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

    fun setJoystickLightEnabled(context: Context, enabled: Boolean): Boolean {
        val value = if (enabled) "1,1" else "0,0"
        val reply = HardwareBridgeClient.request(context, "LIGHTS\t$value")
        if (reply != listOf("LIGHTS", value) ||
            systemValue(context, KEY_JOYSTICK_LIGHT_ENABLED) != value ||
            systemValue(context, KEY_JOYSTICK_HANDLE_LIGHT_ENABLED) != value) {
            throw HardwareControlException("摇杆灯状态读回不一致，请刷新状态。")
        }
        return enabled
    }

    fun toggleJoystickLight(context: Context): Boolean =
        setJoystickLightEnabled(context, !isJoystickLightEnabled(context))

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
    fun isChargePowerLimit5V(context: Context): Boolean = isChargePowerLimitEnabled(context)

    fun setChargePowerLimit5V(context: Context, enabled: Boolean): Boolean {
        val value = if (enabled) "1" else "0"
        setSystem(context, KEY_CHARGE_POWER_LIMIT, value)
        return enabled
    }

    fun toggleChargePowerLimit(context: Context): Boolean {
        val next = !isChargePowerLimit5V(context)
        return setChargePowerLimit5V(context, next)
    }

    fun isChargingSeparationEnabled(context: Context): Boolean = systemValue(context, KEY_CHARGING_SEPARATION) == "1"

    fun setChargingSeparationEnabled(context: Context, enabled: Boolean): Boolean {
        val value = if (enabled) "1" else "0"
        setSystem(context, KEY_CHARGING_SEPARATION, value)
        return enabled
    }

    fun toggleChargingSeparation(context: Context): Boolean {
        val next = !isChargingSeparationEnabled(context)
        return setChargingSeparationEnabled(context, next)
    }

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

    fun setAirplaneMode(context: Context, enabled: Boolean): Boolean {
        try {
            check(Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, if (enabled) 1 else 0))
            try {
                context.sendBroadcast(Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", enabled))
            } catch (_: Exception) {}
        } catch (error: Exception) {
            throw HardwareControlException("飞行模式切换失败，请刷新状态。", error)
        }
        if (isAirplaneModeOn(context) != enabled) throw HardwareControlException("飞行模式读回不一致。")
        return enabled
    }

    fun toggleAirplaneMode(context: Context): Boolean =
        setAirplaneMode(context, !isAirplaneModeOn(context))

    // --- 屏幕方向规则 (支持固定横屏与传感器横屏，并对全局其他应用生效) ---
    fun getOrientationMode(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_ORIENTATION_MODE, ORIENTATION_LANDSCAPE)
    }

    fun setOrientationMode(context: Context, mode: Int) {
        // 保存偏好设置
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ORIENTATION_MODE, mode)
            .apply()

        // 1. 系统级强制横屏开关（触发 com.odin.settings OEM 服务生成全局顶层横屏浮层，拦截并纠正所有第三方应用方向）
        runCatching {
            Settings.System.putInt(
                context.contentResolver,
                SYSTEM_KEY_FORCE_LANDSCAPE,
                if (mode == ORIENTATION_LANDSCAPE) 1 else 0
            )
        }

        // 2. 系统重力感应与屏幕旋转设置 (ACCELEROMETER_ROTATION 与 USER_ROTATION)
        runCatching {
            if (mode == ORIENTATION_LANDSCAPE) {
                // 固定横屏：关闭重力感应自动翻转，并固定为默认横屏 (Surface.ROTATION_90 即 1)
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.ACCELEROMETER_ROTATION,
                    0
                )
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.USER_ROTATION,
                    1
                )
            } else {
                // 传感器横屏：开启重力传感器自适应旋转
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.ACCELEROMETER_ROTATION,
                    1
                )
            }
        }
    }

    fun applyOrientation(activity: Activity, mode: Int) {
        setOrientationMode(activity, mode)
        activity.requestedOrientation = when (mode) {
            ORIENTATION_SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    // --- 启动项与默认桌面设置 ---
    const val KEY_BOOT_AUTO_START = "boot_auto_start_enabled"

    fun isBootAutoStartEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BOOT_AUTO_START, true)
    }

    fun setBootAutoStartEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BOOT_AUTO_START, enabled)
            .apply()
    }

    fun isDefaultHome(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    fun requestDefaultHomeRole(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    if (context !is Activity) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }
            }
        }
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}
