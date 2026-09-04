package com.odin.desktop.shader.preview

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.odin.desktop.data.db.OdinDatabase
import com.odin.desktop.shader.model.GameNativeShaderSettings
import com.odin.desktop.shader.engine.VideoShaderEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Uses the same GPU effect chain as a future in-process renderer integration. */
class ShaderPreviewActivity : ComponentActivity() {
    private lateinit var preview: ShaderPreviewView
    private lateinit var status: TextView
    private lateinit var originalButton: Button
    private var showOriginal = false
    private var effects = GameNativeShaderSettings()
    private val sourceFile get() = File(filesDir, "shader_preview/source.png")
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bitmap = contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "无法读取图片" }
                        BitmapFactory.decodeStream(input, null, sourceDecodeOptions()) ?: error("请选择有效的游戏截图")
                    }
                    require(bitmap.width.toLong() * bitmap.height <= 32_000_000L) { "图片过大" }
                    sourceFile.parentFile?.mkdirs()
                    sourceFile.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
                loadSource()
            }.onFailure { status.text = it.message ?: "截图导入失败" }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        preview = ShaderPreviewView(this) { message -> runOnUiThread { status.text = message } }
        val root = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
            setBackgroundColor(0xdd10141a.toInt())
        }
        status = TextView(this).apply { setTextColor(0xfff0f0f0.toInt()); textSize = 14f }
        val buttons = LinearLayout(this)
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { action() }
            buttons.addView(this)
        }
        button("返回") { finish() }
        button("导入无滤镜截图") { picker.launch("image/*") }
        originalButton = button("显示原图 · X") { toggleOriginal() }
        button("原生接入状态") { showRuntimeInfo() }
        controls.addView(buttons)
        controls.addView(status)
        root.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        setContentView(root)

        lifecycleScope.launch {
            val pkg = intent.getStringExtra("package_name")
            effects = withContext(Dispatchers.IO) {
                intent.getStringExtra("effects_json")?.let(GameNativeShaderSettings::fromJson)
                    ?: pkg?.let { OdinDatabase.getDatabase(applicationContext).appShaderConfigDao().getConfig(it)?.effects }
                    ?: GameNativeShaderSettings()
            }
            preview.setEffects(effects)
            loadSource()
            val debug = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
            if (debug && intent.getBooleanExtra("render_suite", false)) preview.runValidationSuite()
        }
        if (intent.getBooleanExtra("show_runtime_info", false)) showRuntimeInfo()
    }

    private suspend fun loadSource() {
        val bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(sourceFile.absolutePath, sourceDecodeOptions())
        }
        if (bitmap == null) status.text = "导入关闭所有滤镜后的游戏截图；预览按屏幕分辨率运行。"
        else {
            preview.setImage(bitmap)
            status.text = "${bitmap.width} × ${bitmap.height} 原图 · ${effects.family} · 屏幕分辨率渲染 · X 对比原图"
        }
    }

    // ADB screenshots can be Display P3. Match the preview surface and exported PNGs to sRGB.
    private fun sourceDecodeOptions() = BitmapFactory.Options().apply {
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
    }

    private fun toggleOriginal() {
        showOriginal = !showOriginal
        preview.showOriginal(showOriginal)
        originalButton.text = if (showOriginal) "显示滤镜 · X" else "显示原图 · X"
    }

    private fun showRuntimeInfo() {
        AlertDialog.Builder(this)
            .setTitle("原生渲染接入")
            .setMessage("这里在 GPU 上处理你导入的截图，可对比全部滤镜组合。\n\n实时游戏接入尚未完成。需要在目标应用的 Vulkan / OpenGL 渲染流程中加载效果；仅开启桌面选项无法给任意第三方应用注入 Shader。\n\n现有轻量扫描线覆盖层只支持 Vulkan CRT。其他效果在原生接入前仅供截图预览，设置会按应用保存。")
            .setPositiveButton("知道了", null).show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_X && event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0) toggleOriginal()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B && event.action == KeyEvent.ACTION_UP) {
            finish(); return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() { super.onStart(); VideoShaderEngine.beginControlSession(this) }
    override fun onStop() { VideoShaderEngine.endControlSession(this); super.onStop() }
    override fun onResume() { super.onResume(); preview.onResume() }
    override fun onPause() { preview.onPause(); super.onPause() }
}
