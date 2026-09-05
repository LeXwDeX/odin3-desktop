package com.odin.desktop.shader.engine

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import com.odin.desktop.R
import com.odin.desktop.data.db.OdinDatabase
import com.odin.desktop.service.fan.AppMonitorAccessibilityService
import com.odin.desktop.shader.model.AppShaderConfigEntity
import com.odin.desktop.shader.overlay.ShaderOverlayView
import com.odin.desktop.shader.overlay.ShaderTileService
import com.odin.desktop.shader.pipeline.AgslVideoShaderPipeline
import com.odin.desktop.shader.repository.ShaderConfigWrites
import com.odin.desktop.shader.runtime.ShaderRuntime
import com.odin.desktop.shader.runtime.ShaderRuntimeState
import com.odin.desktop.shader.runtime.ShaderStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Main-thread owner of target identity, configuration revision and overlay evidence. */
object VideoShaderEngine {
    private var overlayView: ShaderOverlayView? = null
    private var applicationContext: Context? = null
    private var currentForegroundPackage: String? = null
    private var foregroundLookup: Job? = null
    private var healthCheck: Job? = null
    private var controlSessions = 0
    private var systemWindowVisible = false
    private var generation = 0L
    private var loadedConfig: AppShaderConfigEntity? = null
    private val toggleMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val mutableState = MutableStateFlow(ShaderRuntimeState())
    val state = mutableState.asStateFlow()

    fun currentTargetPackage(context: Context): String? = currentForegroundPackage?.takeUnless {
        it == context.packageName || it == "com.android.systemui" || it == "android"
    }

    // A covered disabled/unsupported filter may keep that more useful status. The
    // window transition still needs a refresh before a later configuration change.
    fun needsForegroundRefresh(): Boolean = systemWindowVisible ||
        mutableState.value.status in setOf(ShaderStatus.PAUSED, ShaderStatus.UNKNOWN)

    private fun publish(status: ShaderStatus, enabled: Boolean? = loadedConfig?.isEnabled) {
        val next = ShaderRuntimeState(currentForegroundPackage, enabled, status)
        if (mutableState.value == next) return
        mutableState.value = next
        applicationContext?.let(ShaderTileService::requestRefresh)
    }

    fun beginControlSession(context: Context) {
        applicationContext = context.applicationContext
        controlSessions++
        invalidateOverlay()
        publish(coveredStatus(context))
    }

    private fun coveredStatus(context: Context): ShaderStatus {
        if (currentTargetPackage(context) == null) return ShaderStatus.NO_TARGET
        val config = loadedConfig ?: return ShaderStatus.UNKNOWN
        return statusForSelection(context, config.packageName, config.isEnabled, config.effects)
    }

    fun endControlSession(context: Context) {
        controlSessions = (controlSessions - 1).coerceAtLeast(0)
        if (controlSessions == 0) {
            // Wait for the actual focused window; never restore a remembered game's success.
            invalidateOverlay()
            publish(ShaderStatus.UNKNOWN)
            AppMonitorAccessibilityService.requestRefresh()
        }
    }

    fun refreshConfig(context: Context, packageName: String) {
        if (currentForegroundPackage == packageName) reload(context)
    }

    fun onForegroundUnknown(context: Context) {
        applicationContext = context.applicationContext
        currentForegroundPackage = null
        loadedConfig = null
        invalidateOverlay()
        publish(ShaderStatus.UNKNOWN, null)
    }

    fun onSystemWindowForeground(context: Context) {
        applicationContext = context.applicationContext
        systemWindowVisible = true
        invalidateOverlay()
        publish(coveredStatus(context))
    }

    fun onForegroundPackageChanged(context: Context, packageName: String) {
        applicationContext = context.applicationContext
        // Our control/preview window covers the game but does not become the game target.
        if (controlSessions > 0 && packageName == context.packageName) return
        systemWindowVisible = false
        currentForegroundPackage = packageName
        reload(context)
    }

    private fun reload(context: Context) {
        applicationContext = context.applicationContext
        invalidateOverlay()
        loadedConfig = null
        val target = currentTargetPackage(context)
        if (target == null || !ShaderRuntime.resolve(context, target).hasTarget) {
            publish(ShaderStatus.NO_TARGET, null)
            return
        }
        val revision = generation
        publish(ShaderStatus.CHECKING, null)
        foregroundLookup = scope.launch {
            try {
                val config = withContext(Dispatchers.IO) {
                    OdinDatabase.getDatabase(context.applicationContext).appShaderConfigDao().getConfig(target)
                        ?: AppShaderConfigEntity.defaultFor(target).copy(isEnabled = false)
                }
                if (generation != revision || currentForegroundPackage != target) return@launch
                loadedConfig = config
                applyLoadedConfig(context, config, revision)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (generation == revision) publish(ShaderStatus.FAILED, null)
                Log.e("VideoShaderEngine", "Could not load shader config", failure)
            }
        }
    }

    fun toggleCurrentAppShader(context: Context, onToggled: (Boolean) -> Unit) {
        val target = currentTargetPackage(context)
        if (controlSessions > 0 || target == null || !ShaderRuntime.resolve(context, target).hasTarget) {
            Toast.makeText(context, R.string.text_return_to_the_game_before_toggling_its, Toast.LENGTH_SHORT).show()
            onToggled(false)
            return
        }
        scope.launch {
            toggleMutex.withLock {
                try {
                    val current = withContext(Dispatchers.IO) {
                        OdinDatabase.getDatabase(context).appShaderConfigDao().getConfig(target)
                            ?: AppShaderConfigEntity.defaultFor(target).copy(isEnabled = false)
                    }
                    // Same write queue as the panel. An old target completion cannot publish for a new app.
                    ShaderConfigWrites.save(context, current.copy(isEnabled = !current.isEnabled)).await().getOrThrow()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (currentForegroundPackage == target) {
                        invalidateOverlay()
                        publish(ShaderStatus.FAILED)
                    }
                    Toast.makeText(context, R.string.text_could_not_save_the_filter_switch, Toast.LENGTH_SHORT).show()
                }
                onToggled(false) // No backend in this build confirms a game's final composed pixels.
            }
        }
    }

    fun statusForSelection(context: Context, target: String?, enabled: Boolean,
                           effects: com.odin.desktop.shader.model.GameNativeShaderSettings): ShaderStatus {
        if (target == null) return ShaderStatus.NO_TARGET
        return ShaderStatus.evaluate(enabled, effects, Build.VERSION.SDK_INT,
            Settings.canDrawOverlays(context),
            AppMonitorAccessibilityService.isRunning && currentForegroundPackage == target,
            controlSessions > 0 || systemWindowVisible)
    }

    private fun applyLoadedConfig(context: Context, config: AppShaderConfigEntity, revision: Long) {
        val status = statusForSelection(context, config.packageName, config.isEnabled, config.effects)
        publish(status)
        if (status != ShaderStatus.CHECKING) return
        val wm = context.applicationContext.getSystemService(WindowManager::class.java)
        try {
            val view = ShaderOverlayView(context.applicationContext,
                onDrawn = {
                    if (generation == revision) publish(ShaderStatus.OVERLAY_UNCONFIRMED)
                },
                onFailure = {
                    scope.launch {
                        if (generation == revision) {
                            invalidateOverlay()
                            publish(ShaderStatus.FAILED)
                        }
                    }
                })
            overlayView = view
            view.applyConfig(config)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                alpha = AgslVideoShaderPipeline.WINDOW_ALPHA
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
            }
            wm.addView(view, params)
            healthCheck = scope.launch {
                delay(500)
                var checks = 0
                while (generation == revision) {
                    val permission = Settings.canDrawOverlays(context)
                    val interactive = context.getSystemService(android.os.PowerManager::class.java).isInteractive
                    if (!permission || !AppMonitorAccessibilityService.isRunning || !interactive || !view.isAttachedToWindow) {
                        invalidateOverlay()
                        publish(when {
                            !permission -> ShaderStatus.PERMISSION_REQUIRED
                            !interactive -> ShaderStatus.PAUSED
                            else -> ShaderStatus.UNKNOWN
                        })
                        return@launch
                    }
                    // Drawing alone does not prove visibility over apps that hide overlays.
                    if (!view.isShown || (++checks >= 4 && mutableState.value.status == ShaderStatus.CHECKING)) {
                        publish(ShaderStatus.UNKNOWN)
                    }
                    delay(500)
                }
            }
        } catch (failure: Exception) {
            Log.e("VideoShaderEngine", "Could not create CRT overlay", failure)
            invalidateOverlay()
            publish(ShaderStatus.FAILED)
        }
    }

    private fun invalidateOverlay() {
        generation++
        foregroundLookup?.cancel()
        foregroundLookup = null
        healthCheck?.cancel()
        healthCheck = null
        val view = overlayView
        overlayView = null
        if (view != null) {
            try {
                applicationContext?.getSystemService(WindowManager::class.java)?.removeViewImmediate(view)
            } catch (failure: Exception) {
                Log.w("VideoShaderEngine", "Could not detach CRT overlay", failure)
            } finally { view.release() }
        }
    }
}
