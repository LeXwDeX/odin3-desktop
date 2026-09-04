package com.odin.desktop.shader.control

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
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.odin.desktop.ui.theme.CyanAccent
import com.odin.desktop.ui.theme.GreenActive
import com.odin.desktop.ui.theme.OdinDesktopTheme
import com.odin.desktop.ui.theme.OrangeWarning
import com.odin.desktop.ui.theme.PureBlack
import com.odin.desktop.ui.theme.TextDim
import com.odin.desktop.ui.theme.TextWhite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 掌机 TVGAME 级电视画质校准与滤镜调整控制台 (TV Display Calibration OSD)。
 * 提供 100% 实体手柄盲操体验，全屏内置广播级 SMPTE 测试图、灰度标定阶梯与特丽珑 OSD 调屏菜单。
 */
class ShaderControlActivity : ComponentActivity() {

    private val repository by lazy {
        ShaderConfigRepository(OdinDatabase.getDatabase(applicationContext).appShaderConfigDao())
    }

    private lateinit var preview: ShaderPreviewView
    private var targetPackage: String? = null
    private var appLabel: String = "独立校准模式"
    private var isGameTarget = false
    private var automaticFamily = ShaderFamily.VULKAN

    // 当前配置与滤镜参数
    private var currentConfig: AppShaderConfigEntity? = null
    private var currentEffects = GameNativeShaderSettings(family = ShaderFamily.VULKAN, enableCRT = true)
    private var isAppFilterEnabled = false

    // 状态流向 Compose 的状态变量
    private val uiEffects = mutableStateOf(currentEffects)
    private val uiAppFilterEnabled = mutableStateOf(false)
    private val uiTargetName = mutableStateOf("独立校准模式")
    private val uiIsGame = mutableStateOf(false)
    private val uiSelectedMenuIndex = mutableIntStateOf(0)
    private val uiIsOsdVisible = mutableStateOf(true)
    private val uiIsBypassActive = mutableStateOf(false)
    private val uiSelectedSignalSource = mutableIntStateOf(0) // 0: SMPTE, 1: 网格, 2: 像素, 3: 自定义截图
    private val uiSaveStatus = mutableStateOf("已就绪")

    private var saveDebounceJob: Job? = null
    private val sourceFile get() = File(filesDir, "shader_preview/source.png")

    private val presetNames = listOf("CRT 特丽珑", "复古街机", "鲜艳游戏", "高清 FXAA", "纯净原画", "自定义")
    private val signalSources = listOf("SMPTE 彩条标定", "几何对齐网格", "复古像素场景", "游戏实机截图")

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                uiSaveStatus.value = "正在载入截图…"
                runCatching {
                    withContext(Dispatchers.IO) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                        checkImageSize(bounds)
                        val bitmap = contentResolver.openInputStream(uri).use {
                            BitmapFactory.decodeStream(it, null, decodeOptions()) ?: error("请选择有效截图")
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
                    uiSaveStatus.value = "截图载入完成"
                }.onFailure { err ->
                    uiSaveStatus.value = "读取截图失败：${err.message?.take(60)}"
                }
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
        automaticFamily = runtime.family

        if (targetPackage == null) {
            appLabel = "独立校准模式"
            isGameTarget = false
            uiTargetName.value = "独立校准模式"
            uiIsGame.value = false
            uiAppFilterEnabled.value = false
            lifecycleScope.launch {
                val loaded = ControlConfigWrites.loadScreenshotSettings(applicationContext)
                applySettings(loaded)
            }
        } else {
            val target = targetPackage!!
            isGameTarget = true
            appLabel = runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(target, 0)).toString()
            }.getOrDefault(target)
            uiTargetName.value = appLabel
            uiIsGame.value = true

            lifecycleScope.launch {
                val stored = runCatching { repository.getConfig(target) }.getOrNull()
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
        val normalized = settings.copy(family = automaticFamily).normalized()
        currentEffects = normalized
        uiEffects.value = normalized
        preview.setEffects(normalized)
    }

    private var hasPendingSave = false

    private fun updateEffectsAndSave(transform: (GameNativeShaderSettings) -> GameNativeShaderSettings) {
        val newSettings = transform(currentEffects).copy(family = automaticFamily).normalized()
        currentEffects = newSettings
        uiEffects.value = newSettings
        preview.setEffects(newSettings)
        hasPendingSave = true

        saveDebounceJob?.cancel()
        saveDebounceJob = lifecycleScope.launch(Dispatchers.IO) {
            delay(250)
            commitPendingSave()
        }
    }

    private suspend fun commitPendingSave() {
        hasPendingSave = false
        val game = targetPackage
        val settings = currentEffects
        val isEnabled = isAppFilterEnabled
        val result = if (game != null) {
            val updated = (currentConfig ?: AppShaderConfigEntity.defaultFor(game))
                .copy(isEnabled = isEnabled)
                .withEffects(settings)
            currentConfig = updated
            ControlConfigWrites.save(applicationContext, updated).await()
        } else {
            ControlConfigWrites.saveScreenshotSettings(applicationContext, settings).await()
        }
        withContext(Dispatchers.Main) {
            if (result.isSuccess) {
                uiSaveStatus.value = "已自动保存"
            } else {
                uiSaveStatus.value = "保存失败: ${result.exceptionOrNull()?.message ?: "写入错误"}"
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
            ControlConfigWrites.save(app, updated)
        } else {
            ControlConfigWrites.saveScreenshotSettings(app, settings)
        }
    }

    private fun toggleAppFilter() {
        if (!isGameTarget) return
        val next = !isAppFilterEnabled
        isAppFilterEnabled = next
        uiAppFilterEnabled.value = next

        saveDebounceJob?.cancel()
        hasPendingSave = false
        saveDebounceJob = lifecycleScope.launch(Dispatchers.IO) {
            val game = targetPackage ?: return@launch
            val updated = (currentConfig ?: AppShaderConfigEntity.defaultFor(game))
                .copy(isEnabled = next)
                .withEffects(currentEffects)
            currentConfig = updated
            val result = ControlConfigWrites.save(applicationContext, updated).await()
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    uiSaveStatus.value = if (next) "已应用到游戏" else "已从游戏停用"
                } else {
                    isAppFilterEnabled = !next
                    uiAppFilterEnabled.value = !next
                    uiSaveStatus.value = "保存失败: ${result.exceptionOrNull()?.message ?: "写入错误"}"
                }
            }
        }
    }

    private fun selectPreset(index: Int) {
        val base = GameNativeShaderSettings(family = automaticFamily, enableCRT = false, enableNTSC = false)
        val selected = when (index) {
            0 -> base.copy(enableCRT = true, scaling = ShaderScaling.NONE, brightness = 0f, contrast = 10f, gamma = 1.0f)
            1 -> base.copy(enableCRT = true, enableVivid = true, brightness = 5f, contrast = 15f, gamma = 0.95f)
            2 -> base.copy(enableVivid = true, enableCRT = false, brightness = 0f, contrast = 10f, gamma = 1.0f)
            3 -> base.copy(enableFXAA = true, enableCRT = false, brightness = 0f, contrast = 0f, gamma = 1.0f)
            4 -> base.copy(enableCRT = false, enableFXAA = false, enableVivid = false, enableToon = false, enableNTSC = false, brightness = 0f, contrast = 0f, gamma = 1.0f)
            else -> currentEffects
        }
        updateEffectsAndSave { selected }
    }

    private fun getPresetIndex(effects: GameNativeShaderSettings): Int {
        val base = GameNativeShaderSettings(family = effects.family, enableCRT = false, enableNTSC = false)
        for (i in 0..4) {
            val p = when (i) {
                0 -> base.copy(enableCRT = true, scaling = ShaderScaling.NONE, brightness = 0f, contrast = 10f, gamma = 1.0f)
                1 -> base.copy(enableCRT = true, enableVivid = true, brightness = 5f, contrast = 15f, gamma = 0.95f)
                2 -> base.copy(enableVivid = true, enableCRT = false, brightness = 0f, contrast = 10f, gamma = 1.0f)
                3 -> base.copy(enableFXAA = true, enableCRT = false, brightness = 0f, contrast = 0f, gamma = 1.0f)
                4 -> base.copy(enableCRT = false, enableFXAA = false, enableVivid = false, enableToon = false, enableNTSC = false, brightness = 0f, contrast = 0f, gamma = 1.0f)
                else -> null
            }
            if (p != null &&
                p.enableCRT == effects.enableCRT &&
                p.enableVivid == effects.enableVivid &&
                p.enableFXAA == effects.enableFXAA &&
                p.enableToon == effects.enableToon &&
                abs(p.brightness - effects.brightness) < 0.05f &&
                abs(p.contrast - effects.contrast) < 0.05f &&
                abs(p.gamma - effects.gamma) < 0.05f
            ) {
                return i
            }
        }
        return 5 // 自定义
    }

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
            val name = presetNames.getOrElse(next) { "预设 $next" }
            presetToast?.cancel()
            presetToast = Toast.makeText(this, "滤镜预设：$name", Toast.LENGTH_SHORT)
            presetToast?.show()
        }
    }

    /**
     * 手柄按键分发：D-Pad 上下选条目、左右即时微调数值、L1/R1 切换预设、X 按住对比、Y 隐藏菜单、A 切换/确认、B 退出。
     */
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
                .background(PureBlack)
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
                        .background(if (isBypassActive) OrangeWarning else GreenActive)
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
                    color = CyanAccent,
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
                        text = "⚡ BYPASS · RAW SOURCE (原画对比中)",
                        color = PureBlack,
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
                LegendChip("D-Pad ↑↓", "选择条目")
                LegendChip("D-Pad ←→", "微调数值")
                LegendChip("L1 / R1", "切换预设")
                LegendChip("X", "按住原画对比", highlight = isBypassActive)
                LegendChip("Y", if (isOsdVisible) "隐藏菜单" else "显示菜单")
                LegendChip("B", "保存退出")
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
                        text = "TV DISPLAY CALIBRATION",
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
                text = "目标: $targetName",
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
                        text = "灰阶标定基准",
                        color = Color(0xFF8899AC),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "至左侧隐约可见·右侧清晰",
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
                        Text(text = "04% 隐约", color = Color(0xFF444444), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    // 基准中灰 (50% 灰)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFF7F7F7F)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "50% 基准", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    // 亮部清晰可见 (96% 白)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFFF2F2F2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "96% 清晰", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                        title = "图像预设",
                        valueText = "◄ ${presetNames.getOrElse(currentPresetIndex) { "自定义" }} ►",
                        isSelected = selectedIndex == 0,
                        valueColor = CyanAccent,
                        onClick = { onItemClick(0) },
                        onLeft = { onItemAdjust(0, -1) },
                        onRight = { onItemAdjust(0, 1) }
                    )
                }

                // 1. 对比度
                item {
                    OsdSliderRow(
                        title = "画面对比度",
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
                        title = "画面亮度",
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
                        title = "色彩伽马",
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
                        title = "CRT 显像管扫描线",
                        enabled = effects.enableCRT,
                        isSelected = selectedIndex == 4,
                        onClick = { onItemClick(4) }
                    )
                }

                // 5. AMD FSR 超分与锐度级别
                item {
                    val isFsrActive = effects.scaling == ShaderScaling.FSR || effects.scaling == ShaderScaling.FSR_ASPECT
                    OsdSliderRow(
                        title = "AMD FSR 超分锐度",
                        value = if (isFsrActive) effects.fsrSharpnessLevel else 0,
                        min = 0,
                        max = 5,
                        unit = if (isFsrActive) "档" else "关闭",
                        isSelected = selectedIndex == 5,
                        onClick = { onItemClick(5) },
                        onLeft = { onItemAdjust(5, -1) },
                        onRight = { onItemAdjust(5, 1) }
                    )
                }

                // 6. 鲜艳增强 (VIVID)
                item {
                    OsdToggleRow(
                        title = "Vivid 鲜艳色彩增强",
                        enabled = effects.enableVivid,
                        isSelected = selectedIndex == 6,
                        onClick = { onItemClick(6) }
                    )
                }

                // 7. FXAA 平滑抗锯齿
                item {
                    OsdToggleRow(
                        title = "FXAA 平滑抗锯齿",
                        enabled = effects.enableFXAA,
                        isSelected = selectedIndex == 7,
                        onClick = { onItemClick(7) }
                    )
                }

                // 8. 测试信号源切换
                item {
                    OsdRow(
                        title = "测试信号源",
                        valueText = "◄ ${signalSources[signalSourceIndex]} ►",
                        isSelected = selectedIndex == 8,
                        valueColor = OrangeWarning,
                        onClick = { onItemClick(8) },
                        onLeft = { onItemAdjust(8, -1) },
                        onRight = { onItemAdjust(8, 1) }
                    )
                }

                // 9. 应用到当前游戏
                item {
                    OsdToggleRow(
                        title = if (isGame) "应用到当前游戏" else "应用到游戏 (未检测到运行中游戏)",
                        enabled = appFilterEnabled && isGame,
                        isSelected = selectedIndex == 9,
                        onClick = { onItemClick(9) }
                    )
                }
            }
        }
    }

    @Composable
    private fun OsdRow(
        title: String,
        valueText: String,
        isSelected: Boolean,
        valueColor: Color = CyanAccent,
        onClick: () -> Unit,
        onLeft: () -> Unit,
        onRight: () -> Unit
    ) {
        val bg = if (isSelected) Color(0x3300E5FF) else Color(0xFF0F1522)
        val border = if (isSelected) CyanAccent else Color(0xFF1B2434)
        val cleanValueText = valueText.removePrefix("◄ ").removeSuffix(" ►")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(bg)
                .border(if (isSelected) 1.5.dp else 1.dp, border, RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isSelected) {
                    Text(text = "►", color = CyanAccent, fontSize = 11.sp)
                }
                Text(
                    text = title,
                    color = if (isSelected) TextWhite else Color(0xFFB0C0D4),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onLeft() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "◄", color = if (isSelected) CyanAccent else Color(0xFF5A708C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = cleanValueText,
                    color = valueColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onRight() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "►", color = if (isSelected) CyanAccent else Color(0xFF5A708C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    private fun OsdSliderRow(
        title: String,
        value: Int,
        min: Int,
        max: Int,
        unit: String,
        isSelected: Boolean,
        onClick: () -> Unit,
        onLeft: () -> Unit,
        onRight: () -> Unit
    ) {
        val bg = if (isSelected) Color(0x3300E5FF) else Color(0xFF0F1522)
        val border = if (isSelected) CyanAccent else Color(0xFF1B2434)
        val isClosed = (value == 0 && unit == "关闭")
        val sign = if (value > 0 && unit == "%") "+" else ""
        val ratio = if (isClosed) 0f else ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f)
        val barCount = 10
        val filled = (ratio * barCount).roundToInt()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(bg)
                .border(if (isSelected) 1.5.dp else 1.dp, border, RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isSelected) {
                    Text(text = "►", color = CyanAccent, fontSize = 11.sp)
                }
                Text(
                    text = title,
                    color = if (isSelected) TextWhite else Color(0xFFB0C0D4),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // ◄ 独立触控减小按钮
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onLeft() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "◄",
                        color = if (isSelected) CyanAccent else Color(0xFF5A708C),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 点阵/段落刻度条
                val barStr = "▮".repeat(filled) + "▯".repeat(barCount - filled)
                Text(
                    text = barStr,
                    color = if (isClosed) Color(0xFF3A4B60) else if (isSelected) CyanAccent else Color(0xFF5A708C),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                // ► 独立触控增加按钮
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onRight() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "►",
                        color = if (isSelected) CyanAccent else Color(0xFF5A708C),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = if (isClosed) "关闭" else "$sign$value$unit",
                    color = if (isClosed) TextDim else if (value != 0) CyanAccent else TextDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }

    @Composable
    private fun OsdSliderRowFloat(
        title: String,
        value: Float,
        min: Float,
        max: Float,
        isSelected: Boolean,
        onClick: () -> Unit,
        onLeft: () -> Unit,
        onRight: () -> Unit
    ) {
        val bg = if (isSelected) Color(0x3300E5FF) else Color(0xFF0F1522)
        val border = if (isSelected) CyanAccent else Color(0xFF1B2434)
        val ratio = ((value - min) / (max - min)).coerceIn(0f, 1f)
        val barCount = 10
        val filled = (ratio * barCount).roundToInt()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(bg)
                .border(if (isSelected) 1.5.dp else 1.dp, border, RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isSelected) {
                    Text(text = "►", color = CyanAccent, fontSize = 11.sp)
                }
                Text(
                    text = title,
                    color = if (isSelected) TextWhite else Color(0xFFB0C0D4),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // ◄ 独立触控减小按钮
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onLeft() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "◄",
                        color = if (isSelected) CyanAccent else Color(0xFF5A708C),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val barStr = "▮".repeat(filled) + "▯".repeat(barCount - filled)
                Text(
                    text = barStr,
                    color = if (isSelected) CyanAccent else Color(0xFF5A708C),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                // ► 独立触控增加按钮
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onRight() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "►",
                        color = if (isSelected) CyanAccent else Color(0xFF5A708C),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = String.format(Locale.US, "%.2f", value),
                    color = CyanAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }

    @Composable
    private fun OsdToggleRow(
        title: String,
        enabled: Boolean,
        isSelected: Boolean,
        onClick: () -> Unit
    ) {
        val bg = if (isSelected) Color(0x3300E5FF) else Color(0xFF0F1522)
        val border = if (isSelected) CyanAccent else Color(0xFF1B2434)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(bg)
                .border(if (isSelected) 1.5.dp else 1.dp, border, RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isSelected) {
                    Text(text = "►", color = CyanAccent, fontSize = 11.sp)
                }
                Text(
                    text = title,
                    color = if (isSelected) TextWhite else Color(0xFFB0C0D4),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
            Text(
                text = if (enabled) "[ ● 开启 ]" else "[ ○ 关闭 ]",
                color = if (enabled) GreenActive else TextDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    @Composable
    private fun LegendChip(button: String, label: String, highlight: Boolean = false) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (highlight) OrangeWarning else Color(0xFF243248))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = button,
                    color = if (highlight) PureBlack else Color(0xFFD4E2F5),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = label,
                color = Color(0xFFA6B7CE),
                fontSize = 11.sp
            )
        }
    }

    companion object {
        const val EXTRA_PREVIEW_ONLY = "preview_only"

        private fun decodeOptions() = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }

        private fun checkImageSize(bounds: BitmapFactory.Options) {
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "请选择有效截图" }
            require(bounds.outWidth.toLong() * bounds.outHeight <= 32_000_000L) { "截图过大" }
        }
    }
}

/** 异步持久化配置队列 */
private object ControlConfigWrites {
    private const val SCREENSHOT_PREFERENCES = "shader_screenshot_preview"
    private const val SCREENSHOT_EFFECTS = "effects_json"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tail: Deferred<Result<Unit>> = CompletableDeferred(Result.success(Unit))

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
