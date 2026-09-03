package com.odin.desktop.shader.engine

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import com.odin.desktop.data.db.OdinDatabase
import com.odin.desktop.shader.model.AppShaderConfigEntity
import com.odin.desktop.shader.overlay.ShaderOverlayView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 掌机 VideoShader 引擎控制器单例
 * 负责分应用着色器调度、全局悬浮视图生命周期及实时开关
 */
object VideoShaderEngine {

    private var overlayView: ShaderOverlayView? = null
    private var windowManager: WindowManager? = null
    private var isAttached = false
    private var currentForegroundPackage: String? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    /**
     * 前台应用切换事件回调
     */
    fun onForegroundPackageChanged(context: Context, packageName: String) {
        currentForegroundPackage = packageName

        // Odin 桌面自身或系统界面不显示滤镜
        if (packageName == context.packageName || packageName == "com.android.systemui") {
            hideOverlay()
            return
        }

        scope.launch {
            val db = OdinDatabase.getDatabase(context)
            val config = withContext(Dispatchers.IO) {
                db.appShaderConfigDao().getConfig(packageName)
            }

            if (config != null && config.isEnabled) {
                showOverlay(context, config)
            } else {
                hideOverlay()
            }
        }
    }

    /**
     * 实时开关当前前台应用的 Shader 滤镜 (供快捷开关/磁贴一键调用)
     */
    fun toggleCurrentAppShader(context: Context, onToggled: ((Boolean) -> Unit)? = null) {
        val pkg = currentForegroundPackage
        if (pkg.isNullOrEmpty() || pkg == context.packageName) {
            onToggled?.invoke(false)
            return
        }

        scope.launch {
            val db = OdinDatabase.getDatabase(context)
            val config = withContext(Dispatchers.IO) {
                val existing = db.appShaderConfigDao().getConfig(pkg)
                    ?: AppShaderConfigEntity.defaultFor(pkg)
                val updated = existing.copy(isEnabled = !existing.isEnabled)
                db.appShaderConfigDao().insertOrUpdate(updated)
                updated
            }

            if (config.isEnabled) {
                showOverlay(context, config)
            } else {
                hideOverlay()
            }
            onToggled?.invoke(config.isEnabled)
        }
    }

    /**
     * 实时预览特定 Shader 配置（供启动台设置弹窗实时比对使用）
     */
    fun previewShader(context: Context, config: AppShaderConfigEntity) {
        showOverlay(context, config)
    }

    /**
     * 停止预览并恢复当前真实前台状态
     */
    fun stopPreview(context: Context) {
        val pkg = currentForegroundPackage
        if (pkg != null && pkg != context.packageName) {
            onForegroundPackageChanged(context, pkg)
        } else {
            hideOverlay()
        }
    }

    private fun showOverlay(context: Context, config: AppShaderConfigEntity) {
        if (!Settings.canDrawOverlays(context)) return

        // 核心准则：Shader 仅在应用内生效，启动台或系统界面严禁加载 Shader
        val pkg = currentForegroundPackage
        if (pkg == null || pkg == context.packageName || pkg == "com.android.systemui") {
            return
        }

        if (windowManager == null) {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }

        val wm = windowManager ?: return

        if (overlayView == null) {
            overlayView = ShaderOverlayView(context.applicationContext)
        }

        val view = overlayView ?: return
        view.applyConfig(config)

        if (!isAttached) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            try {
                wm.addView(view, params)
                isAttached = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun hideOverlay() {
        if (isAttached && overlayView != null && windowManager != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isAttached = false
        }
    }

    fun isShaderActive(): Boolean = isAttached
}
