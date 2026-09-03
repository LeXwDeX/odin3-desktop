package com.odin.desktop.service.fan

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppMonitorAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            currentForegroundPackage = packageName

            // 联动嵌入式 VideoShader 渲染引擎 (针对目标应用自动启停)
            com.odin.desktop.shader.engine.VideoShaderEngine.onForegroundPackageChanged(this, packageName)

            // 广播前台包名变更给风扇守护
            val intent = Intent(ACTION_FOREGROUND_CHANGED).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                setPackage(this@AppMonitorAccessibilityService.packageName)
            }
            sendBroadcast(intent)
        }
    }

    override fun onInterrupt() {}

    companion object {
        const val ACTION_FOREGROUND_CHANGED = "com.odin.desktop.action.FOREGROUND_CHANGED"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"

        var currentForegroundPackage: String? = null
            private set
    }
}
