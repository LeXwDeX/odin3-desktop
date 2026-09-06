package com.odin.desktop.shader.runtime

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import android.os.Process

/** On-demand fallback using already-authorized usage events; never scans interface contents. */
object ForegroundAppResolver {
    private var tracker: ForegroundEventTracker? = null
    private var lastQueryAt = 0L
    fun hasUsageAccess(context: Context): Boolean = runCatching {
        context.getSystemService(AppOpsManager::class.java).unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    @Synchronized fun resolve(context: Context, now: Long = System.currentTimeMillis()): String? {
        if (!hasUsageAccess(context) || !context.getSystemService(PowerManager::class.java).isInteractive ||
            context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            tracker = null
            lastQueryAt = 0L
            return null
        }
        return runCatching {
            val incremental = tracker != null && now >= lastQueryAt && now - lastQueryAt < 60_000
            val since = if (incremental) lastQueryAt - 1_000 else now - 24 * 60 * 60_000L
            val events = context.getSystemService(UsageStatsManager::class.java)
                .queryEvents(since, now) ?: return null
            val state = if (incremental) tracker!! else ForegroundEventTracker(context.packageName)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                state.accept(event.eventType, event.packageName, event.className)
            }
            tracker = state
            lastQueryAt = now
            state.target()?.takeIf { ShaderRuntime.resolve(context, it).hasTarget }
        }.onFailure { tracker = null; lastQueryAt = 0L }.getOrNull()
    }
}

/** Explicit Home/another app wins; only our covering calibration windows are transparent. */
internal class ForegroundEventTracker(private val ownPackage: String) {
    private var current: String? = null
    fun accept(type: Int, owner: String?, className: String?) {
        when (type) {
            UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN,
            UsageEvents.Event.DEVICE_SHUTDOWN -> current = null
            UsageEvents.Event.ACTIVITY_RESUMED -> {
                if (owner == "com.android.systemui" || owner == "android") return
                if (owner == ownPackage && className in setOf(
                        "$ownPackage.shader.control.ShaderControlActivity",
                        "$ownPackage.shader.preview.ShaderPreviewActivity")) return
                current = owner?.takeUnless { it == ownPackage }
            }
        }
    }
    fun target(): String? = current
}
