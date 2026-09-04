package com.odin.desktop.service.fan

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.odin.desktop.shader.engine.VideoShaderEngine

class AppMonitorAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        android.util.Log.d("AppMonitor", "AppMonitorAccessibilityService connected!")
        syncFocusedApplication()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        currentForegroundPackage = null
        android.util.Log.d("AppMonitor", "AppMonitorAccessibilityService destroyed!")
        runCatching {
            sendBroadcast(Intent(ACTION_FOREGROUND_CHANGED).apply {
                putExtra(EXTRA_PACKAGE_NAME, null as String?)
                setPackage(packageName)
            })
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            syncFocusedApplication()
        }
    }

    private fun syncFocusedApplication() {
        val foregroundResult = runCatching { focusedApplicationPackage() }
        if (foregroundResult.isFailure) {
            Log.w("AppMonitor", "Could not read focused application window", foregroundResult.exceptionOrNull())
            if (currentForegroundPackage != null) {
                currentForegroundPackage = null
                sendBroadcast(Intent(ACTION_FOREGROUND_CHANGED).apply {
                    putExtra(EXTRA_PACKAGE_NAME, null as String?)
                    setPackage(this@AppMonitorAccessibilityService.packageName)
                })
            }
            return
        }
        val foreground = foregroundResult.getOrNull() ?: return // QS and other system windows may own focus; retain the confirmed app.

        // MainActivity can clear the engine independently of this service's cache.
        if (foreground == currentForegroundPackage && foreground == VideoShaderEngine.currentTargetPackage(this)) return
        Log.d("AppMonitor", "Foreground package changed to: $foreground (focused application window)")
        currentForegroundPackage = foreground
        VideoShaderEngine.onForegroundPackageChanged(this, foreground)

        sendBroadcast(Intent(ACTION_FOREGROUND_CHANGED).apply {
            putExtra(EXTRA_PACKAGE_NAME, foreground)
            setPackage(this@AppMonitorAccessibilityService.packageName)
        })
    }

    @Suppress("DEPRECATION")
    private fun focusedApplicationPackage(): String? {
        val interactiveWindows = windows
        try {
            for (window in interactiveWindows) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isFocused) continue
                val root = window.root ?: continue
                // Read only package identity; do not traverse nodes or read interface text.
                val owner = try { root.packageName?.toString() } finally { root.recycle() }
                if (owner != null && !isIgnoredWindowOwner(owner)) return owner
            }
        } finally {
            interactiveWindows.forEach { it.recycle() }
        }
        return null
    }

    private fun isIgnoredWindowOwner(owner: String): Boolean =
        owner == "com.android.systemui" || owner == "android" ||
            owner == "com.odin.gameassistant" || owner == "com.odin.mapping" ||
            owner == "com.odin.settings" || owner == "com.google.android.inputmethod.latin" ||
            owner.contains("inputmethod")

    override fun onInterrupt() {}

    companion object {
        const val ACTION_FOREGROUND_CHANGED = "com.odin.desktop.action.FOREGROUND_CHANGED"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"

        var isRunning: Boolean = false
            private set

        var currentForegroundPackage: String? = null
            private set
    }
}
