package com.odin.desktop.shader

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import com.odin.desktop.data.db.OdinDatabase
import com.odin.desktop.service.fan.AppMonitorAccessibilityService
import com.odin.desktop.shader.engine.VideoShaderEngine
import com.odin.desktop.shader.model.AppShaderConfigEntity
import com.odin.desktop.shader.model.GameNativeShaderSettings
import com.odin.desktop.shader.repository.ShaderConfigWrites
import com.odin.desktop.shader.runtime.ShaderRuntimeState
import com.odin.desktop.shader.runtime.ShaderStatus
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/** Fixed isolated target only. Never touches a user's game configuration. Debug APK only. */
class ShaderProbeInstrumentation : Instrumentation() {
    private var verify = false
    private var pauseAt = ""
    private val target = "com.odin.desktop.validationtarget"
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        verify = arguments?.getString("verify") == "true"
        pauseAt = arguments?.getString("pause_at").orEmpty()
        start()
    }

    private fun readState(): ShaderRuntimeState {
        var value = ShaderRuntimeState()
        runOnMainSync { value = VideoShaderEngine.state.value }
        return value
    }

    private fun waitFor(label: String, predicate: (ShaderRuntimeState) -> Boolean): ShaderRuntimeState {
        val until = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < until) {
            val state = readState()
            if (predicate(state)) return state
            SystemClock.sleep(100)
        }
        error("$label: ${readState()}")
    }

    private fun launchFixture(blocked: Boolean = false) {
        targetContext.startActivity(Intent().setClassName(target, "$target.TargetActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("hide_overlays", blocked))
    }

    private fun shell(command: String): String = getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
    }

    override fun onStart() {
        val report = JSONObject()
        try {
            if (verify) runBlocking {
                val context = targetContext
                val dao = OdinDatabase.getDatabase(context).appShaderConfigDao()
                val original = dao.getConfig(target)
                val originalOp = shell("cmd appops get com.odin.desktop SYSTEM_ALERT_WINDOW")
                val mode = Regex("SYSTEM_ALERT_WINDOW: (allow|ignore|deny|default|foreground)").find(originalOp)
                    ?.groupValues?.get(1) ?: "default"
                fun record(name: String, expected: ShaderStatus) {
                    val value = waitFor(name) { it.packageName == target && it.status == expected }
                    report.put(name, value.toString())
                    sendStatus(0, Bundle().apply { putString("stage", name); putString("state", value.toString()) })
                    if (pauseAt == name) SystemClock.sleep(30_000)
                }
                suspend fun save(effects: GameNativeShaderSettings = GameNativeShaderSettings(), enabled: Boolean = true) {
                    ShaderConfigWrites.save(context, AppShaderConfigEntity.defaultFor(target)
                        .copy(isEnabled = enabled).withEffects(effects)).await().getOrThrow()
                }
                try {
                    shell("cmd appops set com.odin.desktop SYSTEM_ALERT_WINDOW allow")
                    context.startActivity(Intent().setClassName(context, "com.odin.desktop.ui.MainActivity")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    // Starting instrumentation force-stops the target and Android can mark its
                    // accessibility service crashed. Rebind only this already-enabled component.
                    val enabled = android.provider.Settings.Secure.getString(context.contentResolver,
                        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                    val component = "com.odin.desktop/com.odin.desktop.service.fan.AppMonitorAccessibilityService"
                    check(component in enabled.split(":")) { "App monitoring must already be enabled" }
                    check(enabled.matches(Regex("[A-Za-z0-9_./:$]*")))
                    val without = enabled.split(":").filter { it != component }.joinToString(":")
                    try {
                        shell("settings put secure enabled_accessibility_services '$without'")
                        SystemClock.sleep(500)
                    } finally {
                        shell("settings put secure enabled_accessibility_services '$enabled'")
                    }
                    val deadline = SystemClock.uptimeMillis() + 8_000
                    while (!AppMonitorAccessibilityService.isRunning && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
                    check(AppMonitorAccessibilityService.isRunning) { "Enable app monitoring before this test" }
                    save(enabled = false)
                    launchFixture()
                    record("disabled", ShaderStatus.DISABLED)
                    // Keep the real focused fixture and the monitor's package cache unchanged.
                    // Returning from a system window must clear coverage even while disabled.
                    runOnMainSync {
                        VideoShaderEngine.onSystemWindowForeground(context)
                        AppMonitorAccessibilityService.requestRefresh()
                    }
                    val refreshDeadline = SystemClock.uptimeMillis() + 8_000
                    var needsRefresh = true
                    while (needsRefresh && SystemClock.uptimeMillis() < refreshDeadline) {
                        runOnMainSync { needsRefresh = VideoShaderEngine.needsForegroundRefresh() }
                        SystemClock.sleep(100)
                    }
                    check(!needsRefresh) { "System window coverage remained after returning to disabled target" }
                    report.put("system_window_return_refreshes", true)
                    save()
                    record("allowed", ShaderStatus.OVERLAY_UNCONFIRMED)
                    launchFixture(blocked = true)
                    SystemClock.sleep(1_000)
                    val blocked = readState()
                    check(blocked.status in setOf(ShaderStatus.OVERLAY_UNCONFIRMED, ShaderStatus.UNKNOWN)) { blocked }
                    report.put("blocked", blocked.toString())
                    sendStatus(0, Bundle().apply { putString("stage", "blocked"); putString("state", blocked.toString()) })
                    if (pauseAt == "blocked") SystemClock.sleep(30_000)
                    save(GameNativeShaderSettings(enableFXAA = true))
                    record("unsupported_combination", ShaderStatus.PREVIEW_ONLY)
                    launchFixture()
                    save(GameNativeShaderSettings(enableCRT = false))
                    record("no_effect", ShaderStatus.NO_EFFECT)
                    shell("cmd appops set com.odin.desktop SYSTEM_ALERT_WINDOW ignore")
                    save()
                    record("permission_missing", ShaderStatus.PERMISSION_REQUIRED)
                    shell("cmd appops set com.odin.desktop SYSTEM_ALERT_WINDOW allow")
                    runOnMainSync { VideoShaderEngine.refreshConfig(context, target) }
                    record("permission_restored", ShaderStatus.OVERLAY_UNCONFIRMED)
                    runOnMainSync { VideoShaderEngine.onForegroundUnknown(context) }
                    check(readState().packageName == null && readState().status == ShaderStatus.UNKNOWN)
                    report.put("foreground_lost", true)
                    launchFixture()
                    record("foreground_restored", ShaderStatus.OVERLAY_UNCONFIRMED)
                    context.startActivity(Intent().setClassName(context, "com.odin.desktop.ui.MainActivity")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    waitFor("home") { it.status == ShaderStatus.NO_TARGET }
                    report.put("home_clears_game", true)
                } finally {
                    if (original == null) dao.deleteConfig(target) else dao.insertOrUpdate(original)
                    shell("cmd appops set com.odin.desktop SYSTEM_ALERT_WINDOW $mode")
                    runOnMainSync { VideoShaderEngine.onForegroundPackageChanged(context, context.packageName) }
                    report.put("config_restored", dao.getConfig(target) == original)
                    report.put("permission_restored_to", mode)
                }
            } else {
                report.put("state", readState().toString())
            }
        } catch (failure: Throwable) {
            report.put("error", failure.toString())
        }
        finish(if (report.has("error")) 1 else 0, Bundle().apply { putString("report", report.toString()) })
    }
}
