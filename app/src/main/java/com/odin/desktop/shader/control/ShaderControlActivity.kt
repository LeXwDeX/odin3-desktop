package com.odin.desktop.shader.control

import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.R
import com.odin.desktop.shader.model.ShaderPresets
import com.odin.desktop.shader.repository.ShaderConfigWrites
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.os.Bundle
import android.util.AtomicFile
import android.view.KeyEvent
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import com.odin.desktop.shader.preview.TvTestPatternGenerator
import com.odin.desktop.shader.repository.ShaderConfigRepository
import com.odin.desktop.shader.runtime.ShaderRuntime
import com.odin.desktop.ui.theme.OdinDesktopTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * 掌机 TVGAME 级电视画质校准与滤镜调整控制台 (TV Display Calibration OSD)。
 * 提供 100% 实体手柄盲操体验，全屏内置广播级 SMPTE 测试图、灰度标定阶梯与特丽珑 OSD 调屏菜单。
 */
class ShaderControlActivity : AppCompatActivity() {

    private val repository by lazy {
        ShaderConfigRepository(OdinDatabase.getDatabase(applicationContext).appShaderConfigDao())
    }

    private lateinit var preview: ShaderPreviewView
    private var targetPackage: String? = null
    private var appLabel: String = ""
    private var isGameTarget = false
    private var configLoaded = false
    private var editRevision = 0L

    // 当前配置与滤镜参数
    private var currentConfig: AppShaderConfigEntity? = null
    private var currentEffects = GameNativeShaderSettings(family = ShaderFamily.VULKAN, enableCRT = true)
    private var isAppFilterEnabled = false

    // 状态流向 Compose 的状态变量
    private val uiEffects = mutableStateOf(currentEffects)
    private val uiAppFilterEnabled = mutableStateOf(false)
    private val uiTargetName = mutableStateOf("")
    private val uiIsGame = mutableStateOf(false)
    private val uiSelectedMenuIndex = mutableIntStateOf(0)
    private val uiIsOsdVisible = mutableStateOf(true)
    private val uiIsBypassActive = mutableStateOf(false)
    private val uiSelectedSignalSource = mutableIntStateOf(0) // 0: SMPTE, 1: 网格, 2: 像素, 3: 自定义截图
    private val uiSaveStatus = mutableStateOf("")

    private var saveDebounceJob: Job? = null
    private val sourceFile get() = File(filesDir, "shader_preview/source.png")

    private val presetNames get() = ShaderPresets.builtIn.map { getString(it.label) } + getString(R.string.text_custom)
    private val signalSources get() = listOf(getString(R.string.text_smpte_calibration_bars), getString(R.string.text_alignment_grid), getString(R.string.text_retro_pixel_scene), getString(R.string.text_game_screenshot))

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                uiSaveStatus.value = getString(R.string.text_loading_screenshot)
                runCatching {
                    withContext(Dispatchers.IO) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                        checkImageSize(bounds, this@ShaderControlActivity)
                        val bitmap = contentResolver.openInputStream(uri).use {
                            BitmapFactory.decodeStream(it, null, decodeOptions()) ?: error(getString(R.string.text_choose_a_valid_screenshot))
                        }
                        sourceFile.parentFile?.mkdirs()
                        val file = AtomicFile(sourceFile)
                        val output = file.startWrite()
                        try {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                            file.finishWrite(output)
                        } catch (e: Throwable) {
                            file.failWrite(output)
                            throw e
                        }
                        bitmap
                    }
                }.onSuccess { bmp ->
                    uiSelectedSignalSource.intValue = 3
                    preview.setImage(bmp)
                    uiSaveStatus.value = getString(R.string.text_screenshot_loaded)
                }.onFailure { err ->
                    uiSaveStatus.value = getString(R.string.text_could_not_read_screenshot_value, err.message?.take(60))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiSaveStatus.value = getString(R.string.text_ready)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setFinishOnTouchOutside(false)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        preview = ShaderPreviewView(this) { msg ->
            runOnUiThread { uiSaveStatus.value = msg }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!uiIsOsdVisible.value) {
                    uiIsOsdVisible.value = true
                } else {
                    finish()
                }
            }
        })

        val snapshot = targetFromIntent(intent)
        loadTarget(snapshot)

        setContent {
            OdinDesktopTheme {
                TvGameCalibrationScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        VideoShaderEngine.beginControlSession(applicationContext)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    }

    override fun onStop() {
        flushPendingSave()
        VideoShaderEngine.endControlSession(applicationContext)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        preview.onResume()
        targetPackage?.let { VideoShaderEngine.refreshConfig(applicationContext, it) }
    }

    override fun onPause() {
        resetBypassState()
        flushPendingSave()
        preview.onPause()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            resetBypassState()
        }
    }

    private fun resetBypassState() {
        if (uiIsBypassActive.value) {
            preview.showOriginal(false)
            uiIsBypassActive.value = false
        }
    }

    private fun targetFromIntent(intent: Intent): String? =
        if (intent.getBooleanExtra(EXTRA_PREVIEW_ONLY, false)) null
        else intent.getStringExtra("package_name") ?: VideoShaderEngine.currentTargetPackage(this)

    private fun loadTarget(snapshot: String?) {
        val runtime = ShaderRuntime.resolve(applicationContext, snapshot)
        targetPackage = snapshot.takeIf { runtime.hasTarget }

        if (targetPackage == null) {
            appLabel = getString(R.string.text_standalone_calibration)
            isGameTarget = false
            uiTargetName.value = getString(R.string.text_standalone_calibration)
            uiIsGame.value = false
            uiAppFilterEnabled.value = false
            lifecycleScope.launch {
                val loaded = ShaderConfigWrites.loadScreenshotSettings(applicationContext)
                applySettings(loaded)
            }
        } else {
            val target = targetPackage!!
            isGameTarget = true
            appLabel = runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(target, 0)).toString()
            }.getOrDefault(target)
            uiTargetName.value = appLabel
            uiIsGame.value = false

            lifecycleScope.launch {
                val stored = try {
                    repository.getConfig(target)
                } catch (failure: Exception) {
                    uiSaveStatus.value = getString(R.string.shader_status_failed)
                    return@launch
                }
                configLoaded = true
                uiIsGame.value = true
                val active = stored ?: AppShaderConfigEntity.defaultFor(target).copy(isEnabled = false)
                currentConfig = active
                isAppFilterEnabled = active.isEnabled
                uiAppFilterEnabled.value = isAppFilterEnabled
                applySettings(active.effects)
            }
        }

        // 加载默认初始背景画面
        loadSignalSource(uiSelectedSignalSource.intValue)
    }

    private fun loadSignalSource(sourceIndex: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = when (sourceIndex) {
                0 -> TvTestPatternGenerator.generate(TvTestPatternGenerator.PatternType.SMPTE_COLOR_BARS)
                1 -> TvTestPatternGenerator.generate(TvTestPatternGenerator.PatternType.CROSSHATCH_GRID)
                2 -> TvTestPatternGenerator.generate(TvTestPatternGenerator.PatternType.RETRO_PIXEL_SCENE)
                3 -> {
                    if (sourceFile.exists()) {
                        runCatching { BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions()) }.getOrNull()
                            ?: TvTestPatternGenerator.generate(TvTestPatternGenerator.PatternType.SMPTE_COLOR_BARS)
                    } else {
                        TvTestPatternGenerator.generate(TvTestPatternGenerator.PatternType.SMPTE_COLOR_BARS)
                    }
                }
                else -> TvTestPatternGenerator.generate(TvTestPatternGenerator.PatternType.SMPTE_COLOR_BARS)
            }
            withContext(Dispatchers.Main) {
                preview.setImage(bitmap)
            }
        }
    }

    private fun applySettings(settings: GameNativeShaderSettings) {
        val normalized = settings.normalized()
        currentEffects = normalized
        uiEffects.value = normalized
        preview.setEffects(normalized)
    }

    private var hasPendingSave = false

    private fun updateEffectsAndSave(transform: (GameNativeShaderSettings) -> GameNativeShaderSettings) {
        val newSettings = transform(currentEffects).normalized()
        currentEffects = newSettings
        uiEffects.value = newSettings
        preview.setEffects(newSettings)
        hasPendingSave = true
        editRevision++

        saveDebounceJob?.cancel()
        saveDebounceJob = lifecycleScope.launch {
            delay(250)
            commitPendingSave()
        }
    }

    private suspend fun commitPendingSave() {
        hasPendingSave = false
        val game = targetPackage
        val settings = currentEffects
        val isEnabled = isAppFilterEnabled
        val revision = editRevision
        val result = if (game != null) {
            val updated = (currentConfig ?: AppShaderConfigEntity.defaultFor(game))
                .copy(isEnabled = isEnabled)
                .withEffects(settings)
            currentConfig = updated
            ShaderConfigWrites.save(applicationContext, updated).await()
        } else {
            ShaderConfigWrites.saveScreenshotSettings(applicationContext, settings).await()
        }
        withContext(Dispatchers.Main) {
            if (revision != editRevision) return@withContext
            if (result.isSuccess) {
                uiSaveStatus.value = getString(R.string.text_saved_automatically)
            } else {
                uiSaveStatus.value = getString(R.string.text_save_failed_value, result.exceptionOrNull()?.message ?: getString(R.string.text_write_error))
            }
        }
    }

    private fun flushPendingSave() {
        if (!hasPendingSave) return
        hasPendingSave = false
        saveDebounceJob?.cancel()
        saveDebounceJob = null
        val game = targetPackage
        val settings = currentEffects
        val isEnabled = isAppFilterEnabled
        val app = applicationContext
        if (game != null) {
            val updated = (currentConfig ?: AppShaderConfigEntity.defaultFor(game))
                .copy(isEnabled = isEnabled)
                .withEffects(settings)
            currentConfig = updated
            ShaderConfigWrites.save(app, updated)
        } else {
            ShaderConfigWrites.saveScreenshotSettings(app, settings)
        }
    }

    private fun toggleAppFilter() {
        if (!isGameTarget || !configLoaded) return
        val revision = ++editRevision
        val next = !isAppFilterEnabled
        isAppFilterEnabled = next
        uiAppFilterEnabled.value = next

        saveDebounceJob?.cancel()
        hasPendingSave = false
        saveDebounceJob = lifecycleScope.launch {
            val game = targetPackage ?: return@launch
            val updated = (currentConfig ?: AppShaderConfigEntity.defaultFor(game))
                .copy(isEnabled = next)
                .withEffects(currentEffects)
            currentConfig = updated
            val result = ShaderConfigWrites.save(applicationContext, updated).await()
            withContext(Dispatchers.Main) {
                if (revision != editRevision) return@withContext
                if (result.isSuccess) {
                    uiSaveStatus.value = if (next) getString(R.string.shader_request_saved_on) else getString(R.string.shader_request_saved_off)
                } else {
                    isAppFilterEnabled = !next
                    uiAppFilterEnabled.value = !next
                    uiSaveStatus.value = getString(R.string.text_save_failed_value, result.exceptionOrNull()?.message ?: getString(R.string.text_write_error))
                }
            }
        }
    }

    private fun selectPreset(index: Int) {
        val preset = ShaderPresets.builtIn.getOrNull(index) ?: return
        updateEffectsAndSave { preset.settings(currentEffects.family) }
    }

    private fun getPresetIndex(effects: GameNativeShaderSettings): Int =
        ShaderPresets.indexOf(effects).takeIf { it >= 0 } ?: ShaderPresets.builtIn.size

    private var presetToast: Toast? = null

    private fun cyclePreset(delta: Int) {
        val cur = getPresetIndex(uiEffects.value)
        val next = if (cur in 0..4) {
            (cur + delta + 5) % 5
        } else {
            if (delta > 0) 0 else 4
        }
        selectPreset(next)
        if (!uiIsOsdVisible.value) {
            val name = presetNames.getOrElse(next) { getString(R.string.text_preset_value, next) }
            presetToast?.cancel()
            presetToast = Toast.makeText(this, getString(R.string.text_filter_preset_value, name), Toast.LENGTH_SHORT)
            presetToast?.show()
        }
    }

    /**
     * 手柄按键分发：D-Pad 上下选条目、左右即时微调数值、L1/R1 切换预设、X 按住对比、Y 隐藏菜单、A 切换/确认、B 退出。
     */
    // core 1.13.1 restricts its base class; this is the public Activity callback.
    // Preserve normal dispatch for keys not consumed by the launcher.
    @Suppress("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // X 键：按住对比原画，松开恢复
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_X) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                preview.showOriginal(true)
                uiIsBypassActive.value = true
                return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                resetBypassState()
                return true
            }
        }

        // Y 键：切换 OSD 菜单显示/隐藏
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                uiIsOsdVisible.value = !uiIsOsdVisible.value
                return true
            }
        }

        // B 键：如果 OSD 隐藏则唤醒，否则退出 (退出前提交待保存配置)
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B || event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) {
                if (!uiIsOsdVisible.value) {
                    uiIsOsdVisible.value = true
                } else {
                    flushPendingSave()
                    finish()
                }
                return true
            }
        }

        // L1 / R1：快捷轮播切换预设 (5档可应用预设循环)
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_L1 || event.keyCode == KeyEvent.KEYCODE_BUTTON_R1) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val delta = if (event.keyCode == KeyEvent.KEYCODE_BUTTON_L1) -1 else 1
                cyclePreset(delta)
                return true
            }
        }

        // 仅在 OSD 显示时处理导航和微调
        if (uiIsOsdVisible.value && event.action == KeyEvent.ACTION_DOWN) {
            val totalMenuItems = 10
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    uiSelectedMenuIndex.intValue = (uiSelectedMenuIndex.intValue - 1 + totalMenuItems) % totalMenuItems
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    uiSelectedMenuIndex.intValue = (uiSelectedMenuIndex.intValue + 1) % totalMenuItems
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    adjustCurrentItem(delta = -1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    adjustCurrentItem(delta = 1)
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    confirmCurrentItem()
                    return true
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun adjustCurrentItem(delta: Int) {
        when (uiSelectedMenuIndex.intValue) {
            0 -> cyclePreset(delta)
            1 -> updateEffectsAndSave { it.copy(contrast = (it.contrast + delta * 2f).coerceIn(-100f, 100f)) }
            2 -> updateEffectsAndSave { it.copy(brightness = (it.brightness + delta * 2f).coerceIn(-100f, 100f)) }
            3 -> updateEffectsAndSave { it.copy(gamma = (it.gamma + delta * 0.05f).coerceIn(0.5f, 2.5f)) }
            4 -> updateEffectsAndSave { it.copy(enableCRT = !it.enableCRT) }
            5 -> {
                // AMD FSR 超分与锐度联动
                updateEffectsAndSave { current ->
                    val isFsrActive = current.scaling == ShaderScaling.FSR || current.scaling == ShaderScaling.FSR_ASPECT
                    if (!isFsrActive) {
                        if (delta > 0) {
                            current.copy(scaling = ShaderScaling.FSR, fsrSharpnessLevel = 3)
                        } else {
                            current
                        }
                    } else {
                        val nextLevel = current.fsrSharpnessLevel + delta
                        if (nextLevel < 1) {
                            current.copy(scaling = ShaderScaling.NONE)
                        } else {
                            current.copy(scaling = ShaderScaling.FSR, fsrSharpnessLevel = nextLevel.coerceAtMost(5))
                        }
                    }
                }
            }
            6 -> updateEffectsAndSave { it.copy(enableVivid = !it.enableVivid) }
            7 -> updateEffectsAndSave { it.copy(enableFXAA = !it.enableFXAA) }
            8 -> {
                val next = (uiSelectedSignalSource.intValue + delta + signalSources.size) % signalSources.size
                uiSelectedSignalSource.intValue = next
                loadSignalSource(next)
            }
            9 -> toggleAppFilter()
        }
    }

    private fun confirmCurrentItem() {
        when (uiSelectedMenuIndex.intValue) {
            4 -> updateEffectsAndSave { it.copy(enableCRT = !it.enableCRT) }
            5 -> {
                // A 键切换 FSR 开关
                updateEffectsAndSave { current ->
                    val isFsrActive = current.scaling == ShaderScaling.FSR || current.scaling == ShaderScaling.FSR_ASPECT
                    if (isFsrActive) {
                        current.copy(scaling = ShaderScaling.NONE)
                    } else {
                        current.copy(
                            scaling = ShaderScaling.FSR,
                            fsrSharpnessLevel = if (current.fsrSharpnessLevel in 1..5) current.fsrSharpnessLevel else 3
                        )
                    }
                }
            }
            6 -> updateEffectsAndSave { it.copy(enableVivid = !it.enableVivid) }
            7 -> updateEffectsAndSave { it.copy(enableFXAA = !it.enableFXAA) }
            8 -> {
                if (uiSelectedSignalSource.intValue == 3) {
                    imagePicker.launch("image/*")
                } else {
                    adjustCurrentItem(delta = 1)
                }
            }
            9 -> toggleAppFilter()
            else -> adjustCurrentItem(delta = 1)
        }
    }

    // --- Compose 界面绘制 ---

    @Composable
    private fun TvGameCalibrationScreen() {
        val palette = LocalOdinPalette.current
        val effects by uiEffects
        val selectedIndex by uiSelectedMenuIndex
        val isOsdVisible by uiIsOsdVisible
        val isBypassActive by uiIsBypassActive
        val targetName by uiTargetName
        val isGame by uiIsGame
        val appFilterEnabled by uiAppFilterEnabled
        val currentPresetIndex = getPresetIndex(effects)
        val signalSourceIndex by uiSelectedSignalSource
        val saveStatus by uiSaveStatus

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background)
        ) {
            // 1. 全屏底图：ShaderPreviewView GPU 渲染
            AndroidView(
                factory = { preview },
                modifier = Modifier.fillMaxSize()
            )

            // 2. 左上角：复古电视信号指示器 (TV SIGNAL WATERMARK)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC0A0E17))
                    .border(1.dp, Color(0xFF223044), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isBypassActive) palette.warning else palette.active)
                )
                Text(
                    text = "CH-1 · AV-1 SCART · 1080P @ 60Hz",
                    color = Color(0xFFC0D2E8),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "• ${signalSources[signalSourceIndex]}",
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // 3. 原画对比浮水印 (按住 X 键时闪烁显示)
            if (isBypassActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xD9FF9800))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = getString(R.string.text_bypass_raw_source),
                        color = palette.background,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 4. 右侧悬浮：经典 Sony Trinitron / PVM 监视器风格 TV OSD 调屏菜单
            AnimatedVisibility(
                visible = isOsdVisible,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(top = 12.dp, bottom = 64.dp, end = 24.dp)
            ) {
                TvOsdMenuPanel(
                    selectedIndex = selectedIndex,
                    effects = effects,
                    currentPresetIndex = currentPresetIndex,
                    signalSourceIndex = signalSourceIndex,
                    targetName = targetName,
                    isGame = isGame,
                    appFilterEnabled = appFilterEnabled,
                    saveStatus = saveStatus,
                    onItemClick = { index ->
                        uiSelectedMenuIndex.intValue = index
                        if (index == 4 || index == 5 || index == 6 || index == 7 || index == 8 || index == 9) {
                            confirmCurrentItem()
                        }
                    },
                    onItemAdjust = { index, delta ->
                        uiSelectedMenuIndex.intValue = index
                        adjustCurrentItem(delta)
                    }
                )
            }

            // 5. 底部手柄导航指引 HUD (极具主机仪式感)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xD9090D14))
                    .border(1.dp, Color(0xFF1B2433), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendChip("D-Pad ↑↓", getString(R.string.text_select_item))
                LegendChip("D-Pad ←→", getString(R.string.text_adjust_value))
                LegendChip("L1 / R1", getString(R.string.text_change_preset))
                LegendChip("X", getString(R.string.text_hold_to_compare), highlight = isBypassActive)
                LegendChip("Y", if (isOsdVisible) getString(R.string.text_hide_menu) else getString(R.string.text_show_menu))
                LegendChip("B", getString(R.string.text_save_and_exit))
            }
        }
    }

    @Composable
    private fun TvOsdMenuPanel(
        selectedIndex: Int,
        effects: GameNativeShaderSettings,
        currentPresetIndex: Int,
        signalSourceIndex: Int,
        targetName: String,
        isGame: Boolean,
        appFilterEnabled: Boolean,
        saveStatus: String,
        onItemClick: (Int) -> Unit,
        onItemAdjust: (Int, Int) -> Unit
    ) {
        val runtimeState by VideoShaderEngine.state.collectAsState()
        val runtimeStatus = VideoShaderEngine.statusForSelection(this, targetPackage, appFilterEnabled, effects)
        val palette = LocalOdinPalette.current
        val listState = rememberLazyListState()

        LaunchedEffect(selectedIndex) {
            listState.animateScrollToItem(selectedIndex.coerceAtLeast(0))
        }

        Column(
            modifier = Modifier
                .width(430.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xF00A0F18))
                .border(1.5.dp, Color(0xFF1E2D44), RoundedCornerShape(12.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            // OSD 顶部标题与目标应用
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "📺", fontSize = 16.sp)
                    Text(
                        text = getString(R.string.shader_calibration_title),
                        color = Color(0xFF00E5FF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = saveStatus,
                    color = Color(0xFF8B9CB2),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = getString(R.string.text_target_value, targetName) + " · " +
                    getString(if (runtimeState.packageName == targetPackage &&
                        runtimeState.status == com.odin.desktop.shader.runtime.ShaderStatus.FAILED)
                        runtimeState.status.message else runtimeStatus.message),
                color = Color(0xFFA6B7CE),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            // 经典 TV 标定灰阶对比基准块 (Calibration Scope)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF070A10))
                    .border(1.dp, Color(0xFF192230), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = getString(R.string.text_grayscale_reference),
                        color = Color(0xFF8899AC),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = getString(R.string.text_left_barely_visible_right_clear),
                        color = Color(0xFF5E7188),
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 暗部隐约可见 (4% 黑)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFF0C0C0C)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = getString(R.string.text_04_faint), color = Color(0xFF444444), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    // 基准中灰 (50% 灰)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFF7F7F7F)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = getString(R.string.text_50_reference), color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    // 亮部清晰可见 (96% 白)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFFF2F2F2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = getString(R.string.text_96_clear), color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 可滚动的 OSD 参数调节条目列表 (支持手柄上下移动选择、左右直接微调)
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                // 0. 图像预设 (PICTURE PRESET)
                item {
                    OsdRow(
                        title = getString(R.string.text_picture_preset),
                        valueText = getString(R.string.text_value, presetNames.getOrElse(currentPresetIndex) { getString(R.string.text_custom) }),
                        isSelected = selectedIndex == 0,
                        valueColor = palette.accent,
                        onClick = { onItemClick(0) },
                        onLeft = { onItemAdjust(0, -1) },
                        onRight = { onItemAdjust(0, 1) }
                    )
                }

                // 1. 对比度
                item {
                    OsdSliderRow(
                        title = getString(R.string.text_contrast),
                        value = effects.contrast.roundToInt(),
                        min = -100,
                        max = 100,
                        unit = "%",
                        isSelected = selectedIndex == 1,
                        onClick = { onItemClick(1) },
                        onLeft = { onItemAdjust(1, -1) },
                        onRight = { onItemAdjust(1, 1) }
                    )
                }

                // 2. 亮度
                item {
                    OsdSliderRow(
                        title = getString(R.string.text_brightness),
                        value = effects.brightness.roundToInt(),
                        min = -100,
                        max = 100,
                        unit = "%",
                        isSelected = selectedIndex == 2,
                        onClick = { onItemClick(2) },
                        onLeft = { onItemAdjust(2, -1) },
                        onRight = { onItemAdjust(2, 1) }
                    )
                }

                // 3. 色彩伽马 (Gamma)
                item {
                    OsdSliderRowFloat(
                        title = getString(R.string.text_gamma),
                        value = effects.gamma,
                        min = 0.5f,
                        max = 2.5f,
                        isSelected = selectedIndex == 3,
                        onClick = { onItemClick(3) },
                        onLeft = { onItemAdjust(3, -1) },
                        onRight = { onItemAdjust(3, 1) }
                    )
                }

                // 4. CRT 显像管扫描线
                item {
                    OsdToggleRow(
                        title = getString(R.string.text_crt_scanlines),
                        enabled = effects.enableCRT,
                        isSelected = selectedIndex == 4,
                        onClick = { onItemClick(4) }
                    )
                }

                // 5. AMD FSR 超分与锐度级别
                item {
                    val isFsrActive = effects.scaling == ShaderScaling.FSR || effects.scaling == ShaderScaling.FSR_ASPECT
                    OsdSliderRow(
                        title = getString(R.string.text_amd_fsr_sharpness),
                        value = if (isFsrActive) effects.fsrSharpnessLevel else 0,
                        min = 0,
                        max = 5,
                        unit = if (isFsrActive) getString(R.string.text_level) else getString(R.string.text_off),
                        isSelected = selectedIndex == 5,
                        onClick = { onItemClick(5) },
                        onLeft = { onItemAdjust(5, -1) },
                        onRight = { onItemAdjust(5, 1) }
                    )
                }

                // 6. 鲜艳增强 (VIVID)
                item {
                    OsdToggleRow(
                        title = getString(R.string.text_vivid_color_enhancement),
                        enabled = effects.enableVivid,
                        isSelected = selectedIndex == 6,
                        onClick = { onItemClick(6) }
                    )
                }

                // 7. FXAA 平滑抗锯齿
                item {
                    OsdToggleRow(
                        title = getString(R.string.text_fxaa_anti_aliasing),
                        enabled = effects.enableFXAA,
                        isSelected = selectedIndex == 7,
                        onClick = { onItemClick(7) }
                    )
                }

                // 8. 测试信号源切换
                item {
                    OsdRow(
                        title = getString(R.string.text_test_signal),
                        valueText = "◄ ${signalSources[signalSourceIndex]} ►",
                        isSelected = selectedIndex == 8,
                        valueColor = palette.warning,
                        onClick = { onItemClick(8) },
                        onLeft = { onItemAdjust(8, -1) },
                        onRight = { onItemAdjust(8, 1) }
                    )
                }

                // 9. 应用到当前游戏
                item {
                    OsdToggleRow(
                        title = if (isGame) getString(R.string.shader_enable_request) else getString(R.string.text_enable_for_game_no_running_game_detected),
                        enabled = appFilterEnabled && isGame,
                        isSelected = selectedIndex == 9,
                        onClick = { onItemClick(9) }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_PREVIEW_ONLY = "preview_only"

        private fun decodeOptions() = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }

        private fun checkImageSize(bounds: BitmapFactory.Options, context: android.content.Context) {
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { context.getString(R.string.text_choose_a_valid_screenshot) }
            require(bounds.outWidth.toLong() * bounds.outHeight <= 32_000_000L) { context.getString(R.string.text_screenshot_too_large) }
        }
    }
}
