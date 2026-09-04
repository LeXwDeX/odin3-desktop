package com.odin.desktop.shader.engine

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import com.odin.desktop.data.db.OdinDatabase
import com.odin.desktop.shader.model.AppShaderConfigEntity
import com.odin.desktop.shader.overlay.ShaderOverlayView
import com.odin.desktop.shader.overlay.ShaderTileService
import com.odin.desktop.shader.pipeline.AgslVideoShaderPipeline
import com.odin.desktop.shader.runtime.ShaderRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 掌机 VideoShader 引擎控制器单例
 * 负责分应用着色器调度、全局悬浮视图生命周期及实时开关
 */
object VideoShaderEngine {
    private var overlayView: ShaderOverlayView? = null
    private var windowManager: WindowManager? = null
    private var applicationContext: Context? = null
    private var isAttached = false
    private var currentForegroundPackage: String? = null
    private var foregroundLookup: Job? = null
    private var controlSessions = 0
    private val toggleMutex = Mutex()
    private val frameInputNoticeShownFor = mutableSetOf<String>()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    fun currentTargetPackage(context: Context): String? = currentForegroundPackage?.takeUnless {
        it == context.packageName || it == "com.android.systemui" || it == "android"
    }

    /** Keep the game target while its controls or screenshot preview cover it. */
    @Suppress("UNUSED_PARAMETER")
    fun beginControlSession(context: Context) {
        controlSessions += 1
        foregroundLookup?.cancel()
        hideOverlay()
    }

    fun endControlSession(context: Context) {
        controlSessions = (controlSessions - 1).coerceAtLeast(0)
        if (controlSessions == 0) {
            currentForegroundPackage?.let { onForegroundPackageChanged(context, it) }
        }
    }

    fun refreshConfig(context: Context, packageName: String) {
        if (currentForegroundPackage == packageName && controlSessions == 0) {
            onForegroundPackageChanged(context, packageName)
        }
    }

    fun toggleCurrentAppShader(context: Context, onToggled: (Boolean) -> Unit) {
        val target = currentTargetPackage(context)
        if (controlSessions > 0 || !ShaderRuntime.resolve(context, target).hasTarget || target == null) {
            Toast.makeText(context, "请先返回游戏，再切换滤镜", Toast.LENGTH_SHORT).show()
            onToggled(false)
            return
        }
        scope.launch {
            toggleMutex.withLock {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val dao = OdinDatabase.getDatabase(context.applicationContext).appShaderConfigDao()
                        val current = dao.getConfig(target)
                            ?: AppShaderConfigEntity.defaultFor(target).copy(isEnabled = false)
                        dao.insertOrUpdate(current.copy(isEnabled = !current.isEnabled))
                    }
                }.onSuccess {
                    if (currentForegroundPackage == target && controlSessions == 0) {
                        val config = withContext(Dispatchers.IO) {
                            OdinDatabase.getDatabase(context).appShaderConfigDao().getConfig(target)
                        }
                        if (config != null && config.isEnabled) showOverlay(context, config) else hideOverlay()
                    }
                }.onFailure {
                    Log.e("VideoShaderEngine", "Could not save shader toggle", it)
                    Toast.makeText(context, "滤镜开关保存失败", Toast.LENGTH_SHORT).show()
                }
                onToggled(isShaderActive())
            }
        }
    }

    /**
     * 前台应用切换事件回调
     */
    fun onForegroundPackageChanged(context: Context, packageName: String) {
        foregroundLookup?.cancel()
        if (currentForegroundPackage != packageName) hideOverlay()
        currentForegroundPackage = packageName

        // Odin 桌面自身或系统界面不显示滤镜
        if (controlSessions > 0 || packageName == context.packageName || packageName == "com.android.systemui") {
            hideOverlay()
            return
        }

        foregroundLookup = scope.launch {
            val db = OdinDatabase.getDatabase(context)
            val config = withContext(Dispatchers.IO) {
                db.appShaderConfigDao().getConfig(packageName)
            }
            if (currentForegroundPackage != packageName) return@launch
            android.util.Log.d("VideoShaderEngine", "Package $packageName config: isEnabled=${config?.isEnabled}")

            if (config != null && config.isEnabled) {
                showOverlay(context, config)
            } else {
                hideOverlay()
            }
        }
    }

    private fun showOverlay(context: Context, config: AppShaderConfigEntity) {
        if (controlSessions > 0) {
            hideOverlay()
            return
        }
        val effectiveConfig = config.withEffects(config.effects.copy(
            family = ShaderRuntime.resolve(context, config.packageName).family
        ))
        val effects = effectiveConfig.effects
        if (effects.requiresFrameInput) {
            hideOverlay()
            if (config.isEnabled && currentForegroundPackage == config.packageName &&
                frameInputNoticeShownFor.add(config.packageName)
            ) {
                Toast.makeText(context, "该组合需原生接入，当前仅保存供预览", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (!config.isEnabled || !effects.enableCRT ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !Settings.canDrawOverlays(context)
        ) {
            hideOverlay()
            return
        }

        // 核心准则：Shader 仅在应用内生效，启动台或系统界面严禁加载 Shader
        val pkg = currentForegroundPackage
        if (pkg != config.packageName || pkg == context.packageName || pkg == "com.android.systemui") {
            hideOverlay()
            return
        }

        applicationContext = context.applicationContext
        if (windowManager == null) {
            windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }

        val wm = windowManager ?: return

        if (overlayView == null) {
            overlayView = ShaderOverlayView(context.applicationContext)
        }

        val view = overlayView ?: return
        view.applyConfig(effectiveConfig)

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
                alpha = AgslVideoShaderPipeline.WINDOW_ALPHA
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
            }

            try {
                wm.addView(view, params)
                updateAttachmentState(true)
            } catch (e: Exception) {
                android.util.Log.e("VideoShaderEngine", "Could not attach CRT overlay", e)
                view.release()
                overlayView = null
            }
        }
    }

    private fun hideOverlay() {
        val view = overlayView ?: return
        try {
            if (isAttached) windowManager?.removeViewImmediate(view)
        } catch (e: Exception) {
            android.util.Log.w("VideoShaderEngine", "Could not detach CRT overlay", e)
        } finally {
            updateAttachmentState(false)
            view.release()
            overlayView = null
        }
    }

    private fun updateAttachmentState(attached: Boolean) {
        if (isAttached == attached) return
        isAttached = attached
        applicationContext?.let(ShaderTileService::requestRefresh)
    }

    fun isShaderActive(): Boolean = isAttached
}
