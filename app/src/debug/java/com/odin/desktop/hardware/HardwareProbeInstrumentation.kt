package com.odin.desktop.hardware

import android.app.Instrumentation
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.provider.Settings
import org.json.JSONObject
import java.io.File

/** Read-only by default; fixed write/restore test requires explicit verify_controls=true. Debug only. */
class HardwareProbeInstrumentation : Instrumentation() {
    private var verifyControls = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        verifyControls = arguments?.getString("verify_controls") == "true"
        start()
    }

    override fun onStart() {
        val report = JSONObject()
        fun record(name: String, read: () -> Any?) {
            try {
                report.put(name, read() ?: JSONObject.NULL)
            } catch (error: Throwable) {
                val cause = error.cause ?: error
                report.put(name, JSONObject().put("error", cause.javaClass.simpleName)
                    .put("message", cause.message))
            }
        }
        val context = targetContext
        record("uid") { Process.myUid() }
        record("selinux") { File("/proc/self/attr/current").readText().trim('\u0000', '\n') }
        record("target_sdk") { context.applicationInfo.targetSdkVersion }
        for (permission in listOf("WRITE_SETTINGS", "WRITE_SECURE_SETTINGS", "DUMP", "PACKAGE_USAGE_STATS")) {
            record("permission.$permission") { context.checkSelfPermission("android.permission.$permission") }
        }
        record("can_write_settings") { Settings.System.canWrite(context) }
        for (key in listOf("performance_mode", "fan_mode", "joystick_light_enabled",
            "joystick_handle_light_enabled", "joystick_led_light_picker_color",
            "percent_80_charge_limit", "charging_limit_power_limit")) {
            record("settings.$key") { Settings.System.getString(context.contentResolver, key) }
        }
        record("performance_property") {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
                .invoke(null, "persist.vendor.debug.mode")
        }
        for (node in listOf("state", "duty", "period", "speed")) {
            record("pwm.$node") { File("/sys/class/gpio5_pwm2/$node").readText().trim() }
        }
        var manager: IBinder? = null
        record("manager") {
            manager = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                .invoke(null, "SettingsController") as? IBinder
            manager?.interfaceDescriptor
        }
        // The exact read-only protocol used by the installed OEM Settings APK.
        for ((name, command) in mapOf(
            "performance" to "/system/bin/getprop persist.vendor.debug.mode",
            "fan_speed" to "/system/bin/cat /sys/class/gpio5_pwm2/speed",
        )) {
            record("oem_command.$name") {
                val service = Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String::class.java)
                    .invoke(null, "PServerBinder") as? IBinder
                checkNotNull(service) { "PServerBinder unavailable" }
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeStringArray(arrayOf(command, "1"))
                    check(service.transact(0, data, reply, 0)) { "OEM read unhandled" }
                    reply.createByteArray()?.toString(Charsets.UTF_8)
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }
        }
        for (name in listOf("FanProvider", "LightProvider", "PreformanceProvider")) {
            record("provider.$name") {
                val service = checkNotNull(manager) { "SettingsController unavailable" }
                val request = Parcel.obtain()
                val response = Parcel.obtain()
                val provider = try {
                    request.writeInterfaceToken("com.ro.settings.IExternalControlManager")
                    request.writeString(name)
                    check(service.transact(1, request, response, 0)) { "Provider lookup unhandled" }
                    response.readException()
                    checkNotNull(response.readStrongBinder()) { "Provider unavailable" }
                } finally {
                    request.recycle()
                    response.recycle()
                }
                val info = JSONObject().put("descriptor", provider.interfaceDescriptor)
                if (name == "FanProvider") {
                    for (code in listOf(1, 2, 3, 6, 7, 10)) {
                        val data = Parcel.obtain()
                        val reply = Parcel.obtain()
                        try {
                            data.writeInterfaceToken("com.ro.settings.IFanControlProvider")
                            check(provider.transact(code, data, reply, 0)) { "Fan read unhandled: $code" }
                            reply.readException()
                            info.put("read_$code", if (code == 3) reply.readFloat() else reply.readInt())
                        } finally {
                            data.recycle()
                            reply.recycle()
                        }
                    }
                }
                info
            }
        }
        for (operation in listOf("PERFORMANCE_GET", "FAN_GET", "FAN_TELEMETRY")) {
            record("app.$operation") { HardwareControlClient.request(context, operation).joinToString(",") }
        }
        if (verifyControls) record("control_verification") { verifyAndRestore() }
        finish(0, Bundle().apply { putString("hardware_report", report.toString()) })
    }

    private fun verifyAndRestore(): JSONObject {
        val context = targetContext
        fun request(body: String) = HardwareControlClient.request(context, body)
        val controller = com.odin.desktop.service.fan.HardwareController
        val performance = request("PERFORMANCE_GET")[1]
        val fan = request("FAN_GET")[1]
        val airplane = if (controller.isAirplaneModeOn(context)) "1" else "0"
        check(fan in listOf("0", "4", "5")) { "Restore requires a supported initial fan mode" }
        val keys = listOf("joystick_light_enabled", "joystick_handle_light_enabled",
            "joystick_led_light_picker_color", "percent_80_charge_limit", "charging_limit_power_limit",
            "is_charging_separation")
        val initial = keys.associateWith { Settings.System.getString(context.contentResolver, it) }
        check(initial.values.all { it != null }) { "Restore requires existing settings" }
        check(initial.getValue("joystick_light_enabled") in listOf("0,0", "1,1") &&
            initial.getValue("joystick_handle_light_enabled") in listOf("0,0", "1,1")) { "Asymmetric lighting skipped" }
        val result = JSONObject()
        val restoration = JSONObject()
        try {
            for (value in listOf("1,1", "0,0")) result.put("lights_$value", request("LIGHTS\t$value").toString())
            result.put("color", request("SET\tjoystick_led_light_picker_color\t#ff00e5ff,#ff00e5ff").toString())
            for (value in listOf("0", "1")) result.put("charge_$value", request("CHARGE\t$value").toString())
            for (value in listOf("1", "0")) result.put("bypass_$value", request("SET\tis_charging_separation\t$value").toString())
            // Only change performance with active cooling; never test fan OFF in elevated modes.
            for (mode in listOf("1", "2", "0")) {
                result.put("performance_$mode", request("PERFORMANCE_FAN\t$mode\t4").toString())
            }
            for (mode in listOf("5", "4")) result.put("fan_$mode", request("SET\tfan_mode\t$mode").toString())
            val temperature = controller.getMaxCpuGpuTemp()
            if (temperature.isFinite() && temperature < 55f) {
                result.put("fan_0", request("SET\tfan_mode\t0").toString())
                result.put("fan_resume", request("SET\tfan_mode\t4").toString())
            }
            result.put("telemetry", request("FAN_TELEMETRY").toString())
            for (value in listOf("1", "0")) result.put("airplane_$value", request("AIRPLANE\t$value").toString())
        } catch (failure: Exception) {
            result.put("failure", failure.message)
        } finally {
            // Restore cooling first. Attempt every remaining restoration even if one fails.
            runCatching { request("PERFORMANCE_FAN\t$performance\t$fan") }
                .onSuccess { restoration.put("performance_fan", true) }
                .onFailure { restoration.put("performance_fan", it.message) }
            initial.forEach { (key, value) ->
                runCatching { request("SET\t$key\t$value") }
                    .onSuccess { restoration.put(key, Settings.System.getString(context.contentResolver, key) == value) }
                    .onFailure { restoration.put(key, it.message) }
            }
            result.put("restoration", restoration)
            runCatching { request("AIRPLANE\t$airplane") }
                .onSuccess { restoration.put("airplane", controller.isAirplaneModeOn(context) == (airplane == "1")) }
                .onFailure { restoration.put("airplane", it.message) }
        }
        return result
    }
}
