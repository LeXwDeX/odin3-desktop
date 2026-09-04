package com.odin.desktop.shader.control

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.util.AtomicFile
import android.view.Gravity
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.odin.desktop.data.db.OdinDatabase
import com.odin.desktop.shader.engine.VideoShaderEngine
import com.odin.desktop.shader.model.AppShaderConfigEntity
import com.odin.desktop.shader.model.GameNativeShaderSettings
import com.odin.desktop.shader.model.ShaderFamily
import com.odin.desktop.shader.model.ShaderScaling
import com.odin.desktop.shader.preview.ShaderPreviewView
import com.odin.desktop.shader.repository.ShaderConfigRepository
import com.odin.desktop.shader.runtime.ShaderRuntime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.io.File
import kotlin.math.roundToInt

/** Compact controls for the game snapshot supplied by the Quick Settings tile. */
class ShaderControlActivity : ComponentActivity() {
    private val repository by lazy {
        ShaderConfigRepository(OdinDatabase.getDatabase(applicationContext).appShaderConfigDao())
    }
    private var targetPackage: String? = null
    private var config: AppShaderConfigEntity? = null
    private var screenshotSettings: GameNativeShaderSettings? = null
    private var targetLoadVersion = 0
    private val settingsReady get() = config != null || screenshotSettings != null
    private var automaticFamily = ShaderFamily.VULKAN
    private var pendingWrite: Deferred<Result<Unit>>? = null
    private var saveFailed = false
    private var binding = false
    private var navigating = false
    private var controlSession = false
    private var finishingAfterSave = false
    private var displayedPreset = -1
    private var showOriginal = false
    private var originalBeforeHold: Boolean? = null
    private var parametersBeforeHold: Boolean? = null
    private var parametersFocusBeforeHold: View? = null
    private var hasImage = false
    private var imageJob: Job? = null
    private var initialFocusPending = true
    private var panelFocusBeforeCollapse: View? = null
    private var confirmTarget: View? = null
    private var editingSlider: SeekBar? = null
    private var sliderEntryProgress = 0
    private var choiceDialog: AlertDialog? = null
    private val controllerControls = mutableListOf<View>()
    private val sliderControls = mutableMapOf<SeekBar, SliderControl>()
    private lateinit var controlRoot: ViewGroup
    private lateinit var controllerHint: TextView
    private lateinit var preview: ShaderPreviewView
    private lateinit var imageStatus: TextView
    private lateinit var imageEmpty: TextView
    private lateinit var replaceImage: Button
    private lateinit var originalImage: Button
    private lateinit var panel: View
    private lateinit var revealPanel: Button
    private lateinit var appName: TextView
    private lateinit var runtimeStatus: TextView
    private lateinit var saveStatus: TextView
    private lateinit var enabled: Switch
    private lateinit var presets: Spinner
    private lateinit var more: Button
    private lateinit var advanced: LinearLayout
    private lateinit var sharpness: SeekBar
    private lateinit var sharpnessHint: TextView
    private lateinit var done: Button
    private val editable = mutableListOf<View>()
    private val effectBindings = mutableListOf<(GameNativeShaderSettings) -> Unit>()
    private val presetNames = listOf("CRT", "FXAA", "Vivid", "Toon", "NTSC", "自定义")
    private val sourceFile get() = File(filesDir, "shader_preview/source.png")
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            imageJob = lifecycleScope.launch {
                replaceImage.isEnabled = false
                imageStatus.text = "正在读取截图…"
                runCatching {
                    withContext(Dispatchers.IO) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                        checkImageSize(bounds)
                        val bitmap = contentResolver.openInputStream(uri).use {
                            BitmapFactory.decodeStream(it, null, decodeOptions()) ?: error("请选择有效截图")
                        }
                        try {
                            sourceFile.parentFile?.mkdirs()
                            val file = AtomicFile(sourceFile)
                            val output = file.startWrite()
                            try {
                                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "截图保存失败" }
                                file.finishWrite(output)
                            } catch (error: Throwable) {
                                file.failWrite(output)
                                throw error
                            }
                            bitmap
                        } catch (error: Throwable) {
                            bitmap.recycle()
                            throw error
                        }
                    }
                }.onSuccess(::showImage).onFailure {
                    imageStatus.text = "读取截图失败：${it.message.orEmpty().take(100)}"
                }
                replaceImage.isEnabled = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setFinishOnTouchOutside(false)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        buildPanel()
        setExpanded(savedInstanceState?.getBoolean("expanded") == true)
        showParameters(savedInstanceState?.getBoolean("panel_visible", true) != false)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleControlBack()
        })
        val snapshot = if (savedInstanceState != null) savedInstanceState.getString("target_package")
        else targetFromIntent(intent)
        loadTarget(snapshot)
        imageJob = lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (!sourceFile.exists()) return@withContext null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
                    checkImageSize(bounds)
                    BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions())
                }
            }.onSuccess { it?.let(::showImage) }.onFailure {
                imageStatus.text = "读取截图失败：${it.message.orEmpty().take(100)}"
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!controlSession) {
            VideoShaderEngine.beginControlSession(applicationContext)
            controlSession = true
        }
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    }

    override fun onStop() {
        if (controlSession) {
            VideoShaderEngine.endControlSession(applicationContext)
            controlSession = false
        }
        super.onStop()
    }

    override fun onResume() { super.onResume(); preview.onResume() }
    override fun onPause() {
        confirmTarget?.isPressed = false
        confirmTarget = null
        finishSliderAdjustment(cancel = false)
        restoreHeldControls()
        preview.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        choiceDialog?.setOnDismissListener(null)
        choiceDialog?.dismiss()
        choiceDialog = null
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) restoreHeldControls()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        choiceDialog?.dismiss()
        finishSliderAdjustment(cancel = false)
        restoreHeldControls()
        afterSaved {
            setIntent(intent)
            loadTarget(targetFromIntent(intent))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("target_package", targetPackage)
        outState.putBoolean("expanded", advanced.visibility == View.VISIBLE)
        outState.putBoolean("panel_visible", parametersBeforeHold ?: (panel.visibility == View.VISIBLE))
        super.onSaveInstanceState(outState)
    }

    private fun targetFromIntent(intent: Intent): String? =
        if (intent.getBooleanExtra(EXTRA_PREVIEW_ONLY, false)) null
        else intent.getStringExtra("package_name") ?: VideoShaderEngine.currentTargetPackage(this)

    private fun loadTarget(snapshot: String?) {
        val runtime = ShaderRuntime.resolve(applicationContext, snapshot)
        targetPackage = snapshot.takeIf { runtime.hasTarget }
        val loadVersion = ++targetLoadVersion
        config = null
        screenshotSettings = null
        pendingWrite = null
        saveFailed = false
        automaticFamily = runtime.family
        runtimeStatus.text = runtime.status
        saveStatus.text = ""
        updateEnabledState()
        preview.setEffects(GameNativeShaderSettings(family = automaticFamily, enableCRT = false))
        if (targetPackage == null) {
            appName.text = "截图调参"
            enabled.text = "应用到游戏 · 未打开游戏"
            runtimeStatus.text = "截图预览 · 参数独立保存"
            lifecycleScope.launch {
                ControlConfigWrites.pending().await()
                runCatching { ControlConfigWrites.loadScreenshotSettings(applicationContext) }.onSuccess { settings ->
                    if (loadVersion != targetLoadVersion) return@onSuccess
                    screenshotSettings = settings
                    bindConfig()
                    updateEnabledState()
                }.onFailure {
                    if (loadVersion == targetLoadVersion) saveStatus.text = "读取预览参数失败：${it.message.orEmpty().take(100)}"
                }
            }
            return
        }
        val target = targetPackage ?: return
        enabled.text = "应用到游戏"
        appName.text = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(target, 0)).toString()
        }.getOrDefault(target)
        lifecycleScope.launch {
            // A recreated panel must not read an older row while its previous instance is saving.
            ControlConfigWrites.pending().await()
            runCatching { repository.getConfig(target) }.onSuccess { stored ->
                if (loadVersion != targetLoadVersion) return@onSuccess
                config = stored ?: AppShaderConfigEntity.defaultFor(target).copy(isEnabled = false)
                bindConfig()
                updateEnabledState()
            }.onFailure {
                if (loadVersion == targetLoadVersion) saveStatus.text = "读取设置失败：${it.message.orEmpty().take(100)}"
            }
        }
    }

    private fun buildPanel() {
        val root = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        controlRoot = root
        preview = ShaderPreviewView(this) { message -> runOnUiThread { imageStatus.text = message } }
        preview.isFocusable = false
        preview.isFocusableInTouchMode = false
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))
        imageEmpty = label("更换一张无滤镜截图，在画面上直接调整效果。\n按住 X 对比原图，按住 Y 隐藏参数。", 17f).apply {
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        root.addView(imageEmpty, FrameLayout.LayoutParams(-1, -1))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(10))
            background = GradientDrawable().apply {
                setColor(SURFACE)
                cornerRadius = dp(16).toFloat()
            }
        }
        content.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label("Shader 滤镜", 19f), LinearLayout.LayoutParams(0, -2, 1f))
            addView(Button(this@ShaderControlActivity).apply {
                text = "收起"
                textSize = 12f
                isAllCaps = false
                setOnClickListener { showParameters(false) }
            }, LinearLayout.LayoutParams(dp(66), dp(40)))
        })
        appName = label("正在读取当前应用…", 14f).also {
            it.setTextColor(SECONDARY)
            it.setPadding(0, dp(5), 0, dp(10))
            content.addView(it)
        }
        enabled = toggle("应用到游戏") { value ->
            if (!binding) config?.let { save(it.copy(isEnabled = value).withEffects(effectiveSettings())) }
        }.also { content.addView(it, rowParams()) }
        presets = spinner(presetNames) { index ->
            if (!binding && settingsReady && index != displayedPreset) {
                if (index == presetNames.lastIndex) {
                    displayedPreset = index
                    setExpanded(true)
                }
                else editEffects { preset(index, automaticFamily) }
            }
        }
        content.addView(labeledRow("效果", presets))
        more = Button(this).apply {
            isAllCaps = false
            textSize = 13f
            setOnClickListener { setExpanded(advanced.visibility != View.VISIBLE) }
        }.also { editable += it; content.addView(it, rowParams()) }
        advanced = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        buildAdvanced()
        content.addView(advanced)
        runtimeStatus = label("", 12f).also {
            it.setTextColor(SECONDARY)
            it.setPadding(0, dp(8), 0, dp(8))
            content.addView(it)
        }
        val imageButtons = LinearLayout(this)
        replaceImage = Button(this).apply {
            text = "更换截图"
            textSize = 13f
            isAllCaps = false
            setOnClickListener { imagePicker.launch("image/*") }
        }
        imageButtons.addView(replaceImage, LinearLayout.LayoutParams(0, dp(44), 1f))
        originalImage = Button(this).apply {
            text = "显示原图"
            textSize = 13f
            isAllCaps = false
            isEnabled = false
            setOnClickListener { toggleOriginal() }
        }
        imageButtons.addView(originalImage, LinearLayout.LayoutParams(0, dp(44), 1f))
        content.addView(imageButtons)
        imageStatus = label("选择已关闭滤镜的游戏截图。按住 X 原图 · 按住 Y 隐藏参数 · L1/R1 切换滤镜", 11f).also {
            it.setTextColor(SECONDARY)
            content.addView(it)
        }
        done = Button(this).apply {
            text = "完成"
            isAllCaps = false
            setOnClickListener { finish() }
        }
        content.addView(done, rowParams())
        saveStatus = label("", 12f).also {
            it.setTextColor(SECONDARY)
            it.minHeight = dp(20)
            it.setOnClickListener { if (saveFailed) retrySave() }
            content.addView(it)
        }
        panel = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                // Leave room for the persistent controller hint below the scrollable controls.
                val screenHeight = resources.displayMetrics.heightPixels
                val limit = minOf((screenHeight * 0.90f).toInt(), (screenHeight - dp(104)).coerceAtLeast(dp(120)))
                val available = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) limit
                else minOf(limit, MeasureSpec.getSize(heightMeasureSpec))
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST))
            }
        }.apply {
            isFillViewport = false
            clipToPadding = false
            isFocusable = false
            addView(content)
        }
        root.addView(panel, FrameLayout.LayoutParams(minOf(dp(350), (resources.displayMetrics.widthPixels * 0.9f).toInt()), -2, Gravity.TOP or Gravity.END).apply {
            setMargins(dp(12), dp(12), dp(12), dp(12))
        })
        revealPanel = Button(this).apply {
            text = "调节"
            isAllCaps = false
            setOnClickListener { showParameters(true) }
        }
        root.addView(revealPanel, FrameLayout.LayoutParams(dp(82), dp(44), Gravity.TOP or Gravity.END).apply {
            setMargins(dp(12), dp(12), dp(12), dp(12))
        })
        controllerHint = label("", 12f).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply { setColor(SURFACE); cornerRadius = dp(10).toFloat() }
        }
        root.addView(controllerHint, FrameLayout.LayoutParams(minOf(dp(350), (resources.displayMetrics.widthPixels * 0.9f).toInt()), -2,
            Gravity.BOTTOM or Gravity.END).apply { setMargins(dp(12), dp(12), dp(12), dp(12)) })
        registerControllerControls(root)
        saveStatus.isFocusable = false
        saveStatus.isFocusableInTouchMode = false
        setContentView(root)
        updateControllerHint()
    }

    private fun buildAdvanced() {
        var scalingModes = emptyList<ShaderScaling>()
        val scaling = spinner(emptyList()) { index ->
            if (!binding) scalingModes.getOrNull(index)?.let { selected -> editEffects { it.copy(scaling = selected) } }
        }
        advanced.addView(labeledRow("缩放", scaling))
        effectBindings += { settings ->
            val available = settings.availableScalingModes
            if (scalingModes != available) {
                scalingModes = available
                scaling.adapter = optionAdapter(available.map { scalingLabel(it) })
            }
            scaling.setSelection(available.indexOf(settings.scaling))
        }
        val toggles = listOf(
            effectToggle("CRT", { it.enableCRT }) { s, value -> s.copy(enableCRT = value) },
            effectToggle("FXAA", { it.enableFXAA }) { s, value -> s.copy(enableFXAA = value) },
            effectToggle("Vivid", { it.enableVivid }) { s, value -> s.copy(enableVivid = value) },
            effectToggle("Toon", { it.enableToon }) { s, value -> s.copy(enableToon = value) },
            effectToggle("NTSC", { it.enableNTSC }) { s, value -> s.copy(enableNTSC = value) }
        )
        toggles.chunked(2).forEach { pair ->
            advanced.addView(LinearLayout(this).apply {
                pair.forEach { addView(it, LinearLayout.LayoutParams(0, dp(44), 1f)) }
            })
        }
        slider("亮度", -100f, 100f, 1f, { it.brightness }) { s, value -> s.copy(brightness = value) }
        slider("对比度", -100f, 100f, 1f, { it.contrast }) { s, value -> s.copy(contrast = value) }
        slider("Gamma", 0.5f, 2.5f, 0.05f, { it.gamma }) { s, value -> s.copy(gamma = value) }
        sharpness = slider("FSR 锐度", 1f, 5f, 1f, { it.fsrSharpnessLevel.toFloat() }) { s, value -> s.copy(fsrSharpnessLevel = value.roundToInt()) }
        sharpnessHint = label("", 12f).also {
            it.setTextColor(SECONDARY)
            it.setPadding(0, 0, 0, dp(6))
            advanced.addView(it)
        }
    }

    private fun effectiveSettings(): GameNativeShaderSettings =
        (config?.effects ?: screenshotSettings ?: GameNativeShaderSettings()).copy(family = automaticFamily).normalized()

    private fun editEffects(change: (GameNativeShaderSettings) -> GameNativeShaderSettings) {
        if (!settingsReady) return
        val effects = change(effectiveSettings()).copy(family = automaticFamily).normalized()
        if (effects == effectiveSettings()) return
        val current = config
        if (current != null) save(current.withEffects(effects)) else saveScreenshotSettings(effects)
        bindConfig()
    }

    private fun bindConfig() {
        if (!settingsReady) return
        binding = true
        try {
            enabled.isChecked = config?.isEnabled == true
            val effects = effectiveSettings()
            val matching = (0 until presetNames.lastIndex).firstOrNull { preset(it, automaticFamily) == effects }
            displayedPreset = matching ?: presetNames.lastIndex
            presets.setSelection(displayedPreset)
            effectBindings.forEach { it(effects) }
            updateSharpnessAvailability()
            preview.setEffects(effects)
        } finally {
            binding = false
        }
    }

    private fun save(value: AppShaderConfigEntity) {
        config = value
        trackWrite(ControlConfigWrites.save(applicationContext, value))
    }

    private fun saveScreenshotSettings(value: GameNativeShaderSettings) {
        screenshotSettings = value
        trackWrite(ControlConfigWrites.saveScreenshotSettings(applicationContext, value))
    }

    private fun retrySave() {
        val game = config
        if (game != null) save(game) else screenshotSettings?.let(::saveScreenshotSettings)
    }

    private fun trackWrite(write: Deferred<Result<Unit>>) {
        saveFailed = false
        saveStatus.setTextColor(SECONDARY)
        saveStatus.text = "正在保存…"
        pendingWrite = write
        lifecycleScope.launch {
            val result = write.await()
            if (pendingWrite === write) showSaveResult(result)
        }
    }

    private fun showSaveResult(result: Result<Unit>) {
        saveFailed = result.isFailure
        saveStatus.setTextColor(if (saveFailed) 0xffffa6a6.toInt() else SECONDARY)
        saveStatus.text = if (saveFailed) "保存失败，点此重试：${result.exceptionOrNull()?.message.orEmpty().take(90)}"
        else if (config == null) "预览参数已保存" else "已保存"
        saveStatus.isFocusable = saveFailed
        saveStatus.isFocusableInTouchMode = saveFailed
        ensureControlFocus()
    }

    private fun afterSaved(action: () -> Unit) {
        if (navigating) return
        if (saveFailed) retrySave()
        navigating = true
        updateEnabledState()
        val write = pendingWrite
        val imagePending = imageJob
        lifecycleScope.launch {
            val result = write?.await() ?: Result.success(Unit)
            imagePending?.join()
            navigating = false
            updateEnabledState()
            if (result.isSuccess) action() else showSaveResult(result)
        }
    }

    override fun finish() {
        if (finishingAfterSave || !::saveStatus.isInitialized) {
            super.finish()
        } else afterSaved {
            finishingAfterSave = true
            finish()
        }
    }

    private fun updateEnabledState() {
        editable.forEach { it.isEnabled = settingsReady && !navigating }
        updateSharpnessAvailability()
        if (::enabled.isInitialized) enabled.isEnabled = config != null && !navigating
        if (::done.isInitialized) done.isEnabled = !navigating
        if (::controlRoot.isInitialized) controlRoot.post {
            if (settingsReady && initialFocusPending) {
                initialFocusPending = false
                focusFirstControl()
            } else ensureControlFocus()
        }
    }

    private fun updateSharpnessAvailability() {
        val effects = effectiveSettings()
        val usesDls = effects.family == ShaderFamily.VULKAN && effects.scaling == ShaderScaling.DLS
        val usesSharpness = usesDls || effects.scaling == ShaderScaling.FSR || effects.scaling == ShaderScaling.FSR_ASPECT
        val overriddenByFxaa = effects.family == ShaderFamily.VULKAN && effects.enableFXAA
        sharpness.isEnabled = settingsReady && !navigating && usesSharpness && !overriddenByFxaa
        sharpnessHint.text = when {
            !usesSharpness -> "选择 FSR 缩放后生效"
            overriddenByFxaa -> "FXAA 已开启，会覆盖锐度效果"
            usesDls -> "当前用于 DLS：调节锐化、对比度和饱和度"
            else -> "调节 FSR 缩放的锐化强度"
        }
    }

    private fun setExpanded(expanded: Boolean) {
        val changed = (advanced.visibility == View.VISIBLE) != expanded
        advanced.visibility = if (expanded) View.VISIBLE else View.GONE
        more.text = if (expanded) "收起参数 ▴" else "更多参数 ▾"
        updateControllerHint()
        if (changed && ::controlRoot.isInitialized) advanced.post {
            if (expanded && advanced.visibility == View.VISIBLE && panel.visibility == View.VISIBLE) {
                controllerControls.firstOrNull { it.isEnabled && it.isShown && isInside(it, advanced) }?.requestFocus()
            } else if (!expanded && panel.visibility == View.VISIBLE) more.requestFocus()
        }
    }

    private fun showParameters(visible: Boolean) {
        if (!visible && panel.visibility == View.VISIBLE) panelFocusBeforeCollapse = currentFocus
        panel.visibility = if (visible) View.VISIBLE else View.GONE
        revealPanel.visibility = if (visible) View.GONE else View.VISIBLE
        if (visible) {
            val previous = panelFocusBeforeCollapse
            if (previous != null && previous.isShown && previous.isEnabled) previous.requestFocus() else focusFirstControl()
        } else revealPanel.requestFocus()
        updateControllerHint()
    }

    private fun showImage(bitmap: Bitmap) {
        hasImage = true
        imageEmpty.visibility = View.GONE
        originalImage.isEnabled = true
        imageStatus.text = "${bitmap.width} × ${bitmap.height} · 按住 X 原图 · 按住 Y 隐藏参数 · L1/R1 切换滤镜"
        preview.setImage(bitmap)
        updateOriginalPreview()
    }

    private fun toggleOriginal() {
        if (!hasImage || originalBeforeHold != null) return
        showOriginal = !showOriginal
        updateOriginalPreview()
    }

    private fun updateOriginalPreview() {
        preview.showOriginal(showOriginal)
        originalImage.text = if (showOriginal) "显示滤镜" else "显示原图"
    }

    private fun holdOriginal() {
        if (!hasImage || originalBeforeHold != null) return
        originalBeforeHold = showOriginal
        showOriginal = true
        updateOriginalPreview()
    }

    private fun releaseOriginal() {
        val previous = originalBeforeHold ?: return
        originalBeforeHold = null
        showOriginal = previous
        updateOriginalPreview()
    }

    private fun holdParameters() {
        if (!::panel.isInitialized || parametersBeforeHold != null) return
        parametersBeforeHold = panel.visibility == View.VISIBLE
        parametersFocusBeforeHold = currentFocus
        showParameters(false)
        revealPanel.visibility = View.GONE
    }

    private fun releaseParameters() {
        val previous = parametersBeforeHold ?: return
        val previousFocus = parametersFocusBeforeHold
        parametersBeforeHold = null
        parametersFocusBeforeHold = null
        showParameters(previous)
        if (previousFocus?.isAttachedToWindow == true && previousFocus.isShown) {
            previousFocus.requestFocus()
        }
    }

    private fun restoreHeldControls() {
        choiceDialog?.window?.decorView?.alpha = 1f
        releaseOriginal()
        releaseParameters()
    }

    private fun selectAdjacentPreset(direction: Int) {
        if (!settingsReady || navigating) return
        val current = displayedPreset.takeIf { it in presetNames.indices } ?: 0
        val next = (current + direction + presetNames.size) % presetNames.size
        if (next == presetNames.lastIndex) {
            displayedPreset = next
            presets.setSelection(next)
            setExpanded(true)
        } else {
            editEffects { preset(next, automaticFamily) }
            bindConfig()
        }
    }

    private fun effectToggle(label: String, read: (GameNativeShaderSettings) -> Boolean,
        change: (GameNativeShaderSettings, Boolean) -> GameNativeShaderSettings): Switch {
        val control = toggle(label) { value -> if (!binding) editEffects { change(it, value) } }
        effectBindings += { control.isChecked = read(it) }
        return control
    }

    private fun slider(label: String, minimum: Float, maximum: Float, step: Float,
        read: (GameNativeShaderSettings) -> Float,
        change: (GameNativeShaderSettings, Float) -> GameNativeShaderSettings): SeekBar {
        val text = this.label(label, 13f)
        val seek = SeekBar(this).apply {
            max = ((maximum - minimum) / step).roundToInt()
            keyProgressIncrement = 1
            progressTintList = ColorStateList.valueOf(ACCENT)
            thumbTintList = ColorStateList.valueOf(ACCENT)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser && !binding) editEffects { change(it, minimum + progress * step) }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    finishSliderAdjustment(cancel = false)
                    seekBar.requestFocus()
                }
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
        sliderControls[seek] = SliderControl(label) { progress ->
            editEffects { change(it, minimum + progress.coerceIn(0, seek.max) * step) }
        }
        editable += seek
        advanced.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text, LinearLayout.LayoutParams(dp(116), -2))
            addView(seek, LinearLayout.LayoutParams(0, dp(42), 1f))
        })
        effectBindings += { settings ->
            val value = read(settings)
            seek.progress = ((value - minimum) / step).roundToInt()
            val number = if (step < 1f) String.format(Locale.getDefault(), "%.2f", value) else value.roundToInt().toString()
            text.text = "$label  $number"
            seek.contentDescription = "$label $number"
        }
        return seek
    }

    private fun toggle(text: String, changed: (Boolean) -> Unit): Switch = Switch(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(FOREGROUND)
        setPadding(0, 0, dp(10), 0)
        setOnCheckedChangeListener { _, value -> changed(value) }
        editable += this
    }

    private fun spinner(options: List<String>, selected: (Int) -> Unit): Spinner = object : Spinner(this, Spinner.MODE_DIALOG) {
        override fun performClick(): Boolean {
            if (!isEnabled) return false
            showChoiceDialog(this)
            return true
        }
    }.apply {
        adapter = optionAdapter(options)
        minimumHeight = dp(44)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (parent?.selectedItemPosition == position) selected(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        editable += this
    }

    private fun optionAdapter(options: List<String>) = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, options) {
        init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            super.getView(position, convertView, parent).also { (it as TextView).setTextColor(FOREGROUND) }
        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
            super.getDropDownView(position, convertView, parent).also {
                (it as TextView).setTextColor(FOREGROUND)
                it.minimumHeight = dp(44)
            }
    }

    private fun labeledRow(title: String, control: View) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        control.contentDescription = title
        addView(label(title, 14f), LinearLayout.LayoutParams(dp(68), -2))
        addView(control, LinearLayout.LayoutParams(0, dp(46), 1f))
    }

    private fun label(text: String, size: Float) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(FOREGROUND)
    }

    private fun rowParams() = LinearLayout.LayoutParams(-1, dp(44))
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private data class SliderControl(val label: String, val applyProgress: (Int) -> Unit)

    private fun registerControllerControls(view: View) {
        if (view is Button || view is Switch || view is Spinner || view is SeekBar || view === saveStatus) {
            controllerControls += view
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.foreground = StateListDrawable().apply {
                fun outline(color: Int) = GradientDrawable().apply {
                    setColor(0x168dbbff)
                    setStroke(dp(2), color)
                    cornerRadius = dp(7).toFloat()
                }
                addState(intArrayOf(android.R.attr.state_activated), outline(0xffffd180.toInt()))
                addState(intArrayOf(android.R.attr.state_focused), outline(ACCENT))
                addState(intArrayOf(android.R.attr.state_pressed), outline(ACCENT))
                addState(intArrayOf(), GradientDrawable().apply { setColor(android.graphics.Color.TRANSPARENT) })
            }
            view.onFocusChangeListener = View.OnFocusChangeListener { focused, hasFocus ->
                if (hasFocus) {
                    if (editingSlider != null && editingSlider !== focused && parametersBeforeHold == null) {
                        finishSliderAdjustment(cancel = false, restoreFocus = false)
                    }
                    focused.post {
                        if (focused.isShown) focused.requestRectangleOnScreen(Rect(0, 0, focused.width, focused.height), false)
                    }
                }
                updateControllerHint()
            }
        } else if (view is ViewGroup) {
            for (index in 0 until view.childCount) registerControllerControls(view.getChildAt(index))
        }
    }

    private fun isInside(view: View, ancestor: ViewGroup): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }

    private fun availableControls(): List<View> = controllerControls.filter { it.isShown && it.isEnabled && it.isFocusable }

    private fun focusFirstControl() {
        if (!::panel.isInitialized || parametersBeforeHold != null || choiceDialog != null) return
        if (panel.visibility != View.VISIBLE) {
            revealPanel.requestFocus()
            return
        }
        val available = availableControls()
        (listOf(enabled, presets, more, replaceImage).firstOrNull { it in available }
            ?: available.firstOrNull())?.requestFocus()
    }

    private fun ensureControlFocus() {
        if (!::controlRoot.isInitialized || parametersBeforeHold != null || choiceDialog != null) return
        if (currentFocus !in availableControls()) focusFirstControl()
    }

    private fun moveControllerFocus(direction: Int) {
        val available = availableControls()
        val focused = currentFocus
        if (focused !in available) {
            focusFirstControl()
            return
        }
        val next = FocusFinder.getInstance().findNextFocus(controlRoot, focused, direction)
            ?.takeIf { it in available }
            ?: if (direction == View.FOCUS_UP || direction == View.FOCUS_DOWN) {
                available.getOrNull(available.indexOf(focused) + if (direction == View.FOCUS_DOWN) 1 else -1)
            } else null
        next?.requestFocus()
    }

    private fun startSliderAdjustment(seek: SeekBar) {
        if (!seek.isEnabled || sliderControls[seek] == null) return
        editingSlider = seek
        sliderEntryProgress = seek.progress
        seek.isActivated = true
        updateControllerHint()
    }

    private fun finishSliderAdjustment(cancel: Boolean, restoreFocus: Boolean = true) {
        val seek = editingSlider ?: return
        editingSlider = null
        seek.isActivated = false
        if (cancel) sliderControls[seek]?.applyProgress?.invoke(sliderEntryProgress)
        if (restoreFocus && seek.isShown && seek.isEnabled) seek.requestFocus()
        updateControllerHint()
    }

    private fun updateControllerHint() {
        if (!::controllerHint.isInitialized) return
        controllerHint.visibility = if (panel.visibility == View.VISIBLE && parametersBeforeHold == null) View.VISIBLE else View.GONE
        val editing = editingSlider?.let { sliderControls[it] }
        val focusedSlider = (currentFocus as? SeekBar)?.let { sliderControls[it] }
        controllerHint.text = when {
            editing != null -> "${editing.label}调节中 · ←→ 调整\nA 确认 · B 取消并恢复"
            focusedSlider != null -> "${focusedSlider.label} · A 进入调节 · ↑↓ 移动\n按住 X 原图 · 按住 Y 隐藏参数"
            advanced.visibility == View.VISIBLE -> "方向键移动 · A 确认 · B 收起参数\n按住 X 原图 · 按住 Y 隐藏参数"
            else -> "方向键移动 · A 确认 · B 返回\nL1/R1 切换效果 · 按住 X 原图 / Y 隐藏"
        }
    }

    private fun handleControlBack() {
        when {
            choiceDialog != null -> choiceDialog?.dismiss()
            editingSlider != null -> finishSliderAdjustment(cancel = true)
            panel.visibility == View.VISIBLE && advanced.visibility == View.VISIBLE -> setExpanded(false)
            panel.visibility == View.VISIBLE -> showParameters(false)
            else -> finish()
        }
    }

    private fun showChoiceDialog(control: Spinner) {
        if (choiceDialog != null || !control.isEnabled) return
        finishSliderAdjustment(cancel = false)
        val choices = (0 until control.adapter.count).map { control.adapter.getItem(it).toString() }.toTypedArray()
        if (choices.isEmpty()) return
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("${control.contentDescription ?: "选择"} · A 确认 / B 取消")
            .setSingleChoiceItems(choices, control.selectedItemPosition) { popup, which ->
                control.setSelection(which)
                popup.dismiss()
            }
            .setNegativeButton("取消 (B)") { popup, _ -> popup.dismiss() }
            .create()
        choiceDialog = dialog
        dialog.setOnShowListener {
            dialog.listView.selector = GradientDrawable().apply {
                setColor(0x338dbbff)
                setStroke(dp(2), ACCENT)
                cornerRadius = dp(6).toFloat()
            }
            dialog.listView.requestFocus()
            dialog.listView.setSelection(control.selectedItemPosition.coerceAtLeast(0))
        }
        dialog.setOnDismissListener {
            if (choiceDialog === dialog) choiceDialog = null
            if (control.isShown && control.isEnabled) control.requestFocus() else ensureControlFocus()
        }
        // The popup has its own window, so controller keys must be handled here too.
        dialog.setOnKeyListener { _, key, event ->
            when {
                handleHeldKey(event) -> true
                parametersBeforeHold != null -> true
                key == KeyEvent.KEYCODE_BUTTON_B || key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_ESCAPE -> {
                    if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) dialog.dismiss()
                    true
                }
                key == KeyEvent.KEYCODE_BUTTON_L1 || key == KeyEvent.KEYCODE_BUTTON_R1 -> true
                isConfirmKey(key) -> {
                    if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                        val list = dialog.listView
                        if (list.hasFocus()) {
                            val position = list.selectedItemPosition.takeIf { it >= 0 } ?: list.checkedItemPosition
                            if (position in choices.indices) {
                                control.setSelection(position)
                                dialog.dismiss()
                            }
                        } else dialog.currentFocus?.performClick()
                    }
                    true
                }
                else -> false
            }
        }
        dialog.show()
    }

    private fun isConfirmKey(key: Int): Boolean = key == KeyEvent.KEYCODE_BUTTON_A || key == KeyEvent.KEYCODE_DPAD_CENTER ||
        key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER

    private fun handleHeldKey(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_X) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) holdOriginal()
            else if (event.action == KeyEvent.ACTION_UP) releaseOriginal()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                holdParameters()
                choiceDialog?.window?.decorView?.alpha = 0f
            } else if (event.action == KeyEvent.ACTION_UP) {
                choiceDialog?.window?.decorView?.alpha = 1f
                releaseParameters()
            }
            return true
        }
        return false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (handleHeldKey(event)) return true
        if (parametersBeforeHold != null) return true
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_L1 || event.keyCode == KeyEvent.KEYCODE_BUTTON_R1) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && editingSlider == null &&
                choiceDialog == null && panel.visibility == View.VISIBLE && advanced.visibility != View.VISIBLE
            ) {
                selectAdjacentPreset(if (event.keyCode == KeyEvent.KEYCODE_BUTTON_L1) -1 else 1)
            }
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B || event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) handleControlBack()
            return true
        }
        if (isConfirmKey(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                ensureControlFocus()
                confirmTarget = currentFocus?.takeIf { it in availableControls() }
                confirmTarget?.isPressed = true
            } else if (event.action == KeyEvent.ACTION_UP) {
                val target = confirmTarget
                confirmTarget = null
                target?.isPressed = false
                if (!event.isCanceled && target != null && target === currentFocus && target.isEnabled && target.isShown) {
                    if (editingSlider != null) finishSliderAdjustment(cancel = false)
                    else if (target is SeekBar) startSliderAdjustment(target)
                    else target.performClick()
                }
            }
            return true
        }
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
            else -> null
        }
        if (direction != null) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val seek = editingSlider
                if (seek != null) {
                    if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
                        val progress = (seek.progress + if (direction == View.FOCUS_RIGHT) 1 else -1).coerceIn(0, seek.max)
                        sliderControls[seek]?.applyProgress?.invoke(progress)
                    }
                } else moveControllerFocus(direction)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val EXTRA_PREVIEW_ONLY = "preview_only"
        private const val SURFACE = 0xff171d27.toInt()
        private const val FOREGROUND = 0xffedf2f8.toInt()
        private const val SECONDARY = 0xffa7b3c3.toInt()
        private const val ACCENT = 0xff8dbbff.toInt()

        private fun decodeOptions() = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }

        private fun checkImageSize(bounds: BitmapFactory.Options) {
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "请选择有效截图" }
            require(bounds.outWidth.toLong() * bounds.outHeight <= 32_000_000L) { "截图过大" }
        }

        private fun preset(index: Int, family: ShaderFamily): GameNativeShaderSettings {
            val base = GameNativeShaderSettings(family = family, enableCRT = false)
            return when (index) {
                0 -> base.copy(enableCRT = true)
                1 -> base.copy(enableFXAA = true)
                2 -> base.copy(enableVivid = true)
                3 -> base.copy(enableToon = true)
                4 -> base.copy(enableNTSC = true)
                else -> base
            }
        }

        private fun scalingLabel(mode: ShaderScaling): String = when (mode) {
            ShaderScaling.NONE -> "无"
            ShaderScaling.NEAREST -> "最近邻 · 保持比例"
            ShaderScaling.LINEAR -> "双线性 · 保持比例"
            ShaderScaling.FILL -> "填满 · 裁剪边缘"
            ShaderScaling.STRETCH -> "拉伸"
            ShaderScaling.FSR -> "FSR"
            ShaderScaling.FSR_ASPECT -> "FSR · 保持比例"
            ShaderScaling.DLS -> "DLS"
            ShaderScaling.NATURAL -> "Natural"
        }
    }
}

/** The application-owned queue outlives a panel that is closed or recreated during a Room write. */
private object ControlConfigWrites {
    private const val SCREENSHOT_PREFERENCES = "shader_screenshot_preview"
    private const val SCREENSHOT_EFFECTS = "effects_json"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tail: Deferred<Result<Unit>> = CompletableDeferred(Result.success(Unit))

    @Synchronized
    fun pending(): Deferred<Result<Unit>> = tail

    @Synchronized
    fun save(context: Context, value: AppShaderConfigEntity): Deferred<Result<Unit>> {
        val app = context.applicationContext
        return enqueue {
            OdinDatabase.getDatabase(app).appShaderConfigDao().insertOrUpdate(value)
            withContext(Dispatchers.Main.immediate) { VideoShaderEngine.refreshConfig(app, value.packageName) }
        }
    }

    suspend fun loadScreenshotSettings(context: Context): GameNativeShaderSettings = withContext(Dispatchers.IO) {
        val json = context.applicationContext.getSharedPreferences(SCREENSHOT_PREFERENCES, Context.MODE_PRIVATE)
            .getString(SCREENSHOT_EFFECTS, "").orEmpty()
        GameNativeShaderSettings.fromJson(json)
    }

    fun saveScreenshotSettings(context: Context, value: GameNativeShaderSettings): Deferred<Result<Unit>> {
        val app = context.applicationContext
        return enqueue {
            check(app.getSharedPreferences(SCREENSHOT_PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(SCREENSHOT_EFFECTS, value.toJson()).commit()) { "截图预览参数保存失败" }
        }
    }

    @Synchronized
    private fun enqueue(write: suspend () -> Unit): Deferred<Result<Unit>> {
        val previous = tail
        return scope.async {
            previous.await()
            runCatching { write() }
        }.also { tail = it }
    }
}
