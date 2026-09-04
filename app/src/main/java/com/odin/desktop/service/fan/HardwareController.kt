package com.odin.desktop.service.fan

import com.odin.desktop.R
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
    const val ACTION_FAN_STATE_CHANGED = "com.odin.desktop.action.FAN_STATE_CHANGED"
    private val fanControl = FanControlCoordinator()

    /**
     * 读取 Odin 3 硬件 SoC (CPU 与 GPU) 实时最高温度 (°C)
     */
    fun getMaxCpuGpuTemp(): Float {
        var maxCpuTemp = Float.NaN
        var maxGpuTemp = Float.NaN
        try {
            val dir = java.io.File("/sys/class/thermal")
            val files = dir.listFiles() ?: return Float.NaN
            for (file in files) {
                if (file.name.startsWith("thermal_zone")) {
                    val typeFile = java.io.File(file, "type")
                    if (!typeFile.exists()) continue
                    val type = typeFile.readText().trim()
                    val isCpu = type.contains("cpu", ignoreCase = true)
                    val isGpu = type.contains("gpu", ignoreCase = true)
                    if (isCpu || isGpu) {
                        val tempFile = java.io.File(file, "temp")
                        if (tempFile.exists()) {
                            val raw = tempFile.readText().trim().toLongOrNull() ?: continue
                            val temp = if (raw > 1000) raw / 1000f else raw.toFloat()
                            if (temp in 10f..120f) {
                                if (isCpu) {
                                    maxCpuTemp = if (maxCpuTemp.isNaN()) temp else maxOf(maxCpuTemp, temp)
                                }
                                if (isGpu) {
                                    maxGpuTemp = if (maxGpuTemp.isNaN()) temp else maxOf(maxGpuTemp, temp)
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            return Float.NaN
        }
        // Require both CPU and GPU to have at least one valid reading to prevent partial success
        if (maxCpuTemp.isNaN() || maxGpuTemp.isNaN()) {
            return Float.NaN
        }
        return maxOf(maxCpuTemp, maxGpuTemp)
    }

    fun isAutoFanControlEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_FAN_CONTROL, true)
    }

    @Synchronized
    fun setAutoFanControlEnabled(context: Context, enabled: Boolean) {
        fanControl.setAutoEnabled(fanBackend(context), enabled)
    }

    private fun systemValue(context: Context, key: String): String? =
        Settings.System.getString(context.contentResolver, key)

    private fun setSystem(context: Context, key: String, value: String) {
        val reply = HardwareBridgeClient.request(context, "SET\t$key\t$value")
        if (reply != listOf(key, value) || systemValue(context, key) != value) {
            throw HardwareControlException(context.getString(R.string.text_hardware_readback_differs_from_the_requested_setting))
        }
    }

    // --- CPU 性能模式 ---
    fun getPerformanceMode(context: Context): Int {
        val reply = HardwareBridgeClient.request(context, "PERFORMANCE_GET")
        val value = reply.getOrNull(1)?.toIntOrNull()
        if (reply.size != 2 || reply[0] != "PERFORMANCE" || value == null || value !in 0..2) {
            throw HardwareControlException(context.getString(R.string.text_system_performance_mode_is_unavailable_refresh_the))
        }
        return value
    }

    data class PerfFanResult(val perfMode: Int, val fanMode: Int)

    @Synchronized
    fun cyclePerformanceMode(context: Context): PerfFanResult {
        val next = (getPerformanceMode(context) + 1) % 3
        return setPerformanceMode(context, next)
    }

    @Synchronized
    fun setPerformanceMode(context: Context, mode: Int): PerfFanResult {
        val state = fanControl.setPerformance(fanBackend(context), mode)
        if (state.autoEnabled) {
            context.sendBroadcast(Intent(ACTION_AUTO_FAN_CONFIG_CHANGED).setPackage(context.packageName))
        }
        return PerfFanResult(state.performanceMode, state.fanMode)
    }

    @Synchronized
    fun setPerformanceAndFan(context: Context, mode: Int, fan: Int): PerfFanResult {
        val state = fanControl.setPerformance(fanBackend(context), mode, fan)
        if (state.autoEnabled) {
            context.sendBroadcast(Intent(ACTION_AUTO_FAN_CONFIG_CHANGED).setPackage(context.packageName))
        }
        return PerfFanResult(state.performanceMode, state.fanMode)
    }

    // --- 风扇模式 ---
    fun getFanMode(context: Context): Int {
        val reply = HardwareBridgeClient.request(context, "FAN_GET")
        val value = reply.getOrNull(1)?.toIntOrNull()
        if (reply.size != 2 || reply[0] != "FAN" || value == null || value !in 0..6) {
            throw HardwareControlException(context.getString(R.string.text_cannot_confirm_the_actual_fan_mode_refresh))
        }
        return value
    }

    @Synchronized
    fun cycleFanMode(context: Context): Int {
        val next = when (getFanMode(context)) {
            FAN_OFF -> FAN_SMART
            FAN_SMART -> FAN_SPORT
            else -> FAN_OFF
        }
        return setManualFanMode(context, next)
    }

    @Synchronized
    fun setFanMode(context: Context, mode: Int): Boolean {
        setManualFanMode(context, mode)
        return true
    }

    @Synchronized
    fun setManualFanMode(context: Context, mode: Int): Int =
        fanControl.setManualFan(fanBackend(context), mode)

    @Synchronized
    fun getFanPolicySnapshot(context: Context): FanControlCoordinator.Snapshot =
        fanControl.snapshot(fanBackend(context))

    @Synchronized
    fun applyFanPolicy(
        context: Context,
        expected: FanControlCoordinator.Snapshot,
        mode: Int,
        requestIsCurrent: () -> Boolean
    ): Boolean {
        val applied = fanControl.applyPolicy(fanBackend(context), expected, mode, requestIsCurrent)
        if (applied && expected.fanMode != mode &&
            (expected.autoEnabled || expected.fanMode == FAN_OFF)) {
            // Settings notifications precede OEM settling. Publish completion only after the
            // bridge has confirmed the driver state, including changes made by the watchdog.
            context.sendBroadcast(Intent(ACTION_FAN_STATE_CHANGED).setPackage(context.packageName))
        }
        return applied
    }

    private fun fanBackend(context: Context) = object : FanControlCoordinator.Backend {
        override fun readPerformance(): Int = getPerformanceMode(context)
        override fun readFan(): Int = getFanMode(context)
        override fun readConfiguredFan(): Int = systemValue(context, KEY_FAN_MODE)?.toIntOrNull()
            ?.takeIf { it in 0..6 } ?: throw HardwareControlException(context.getString(R.string.text_cannot_read_the_selected_fan_mode))
        override fun readAutoEnabled(): Boolean = isAutoFanControlEnabled(context)
        override fun writeAutoEnabled(enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_AUTO_FAN_CONTROL, enabled).apply()
            context.sendBroadcast(Intent(ACTION_AUTO_FAN_CONFIG_CHANGED).setPackage(context.packageName))
        }
        override fun writeFan(mode: Int) = setSystem(context, KEY_FAN_MODE, mode.toString())
        override fun writePerformanceAndFan(performance: Int, fan: Int) {
            val reply = HardwareBridgeClient.request(context, "PERFORMANCE_FAN\t$performance\t$fan")
            if (reply != listOf("PERFORMANCE_FAN", performance.toString(), fan.toString())) {
                throw HardwareControlException(context.getString(R.string.text_performance_and_fan_settings_were_not_both))
            }
        }
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
            throw HardwareControlException(context.getString(R.string.text_stick_lighting_readback_differs_refresh_the_status))
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
            throw HardwareControlException(context.getString(R.string.text_charging_limits_were_not_both_applied_refresh))
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
            throw HardwareControlException(context.getString(R.string.text_airplane_mode_could_not_be_changed_refresh), error)
        }
        if (isAirplaneModeOn(context) != enabled) throw HardwareControlException(context.getString(R.string.text_airplane_mode_readback_differs))
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
