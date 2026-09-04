package com.odin.desktop.service.fan

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppMonitorAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            // 严禁响应自身包名！自身弹出 ShaderOverlayView 悬浮窗会触发本包名事件，
            // 若当作切换回桌面会导致刚弹出的 Shader 遮罩被瞬间关闭！
            // 回到启动台时由 MainActivity.onResume() 主动通知关闭，此处必须忽略自身。
            if (packageName == this.packageName) {
                return
            }

            // 过滤输入法和临时系统提示，避免误判离开游戏
            if (packageName == "com.google.android.inputmethod.latin" ||
                packageName.contains("inputmethod") ||
                packageName == "android"
            ) {
                return
            }

            android.util.Log.d("AppMonitor", "Foreground package changed to: $packageName")
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
