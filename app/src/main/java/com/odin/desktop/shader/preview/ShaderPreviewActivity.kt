package com.odin.desktop.shader.preview

import com.odin.desktop.R
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
import androidx.appcompat.app.AppCompatActivity
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
class ShaderPreviewActivity : AppCompatActivity() {
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
                        requireNotNull(input) { getString(R.string.text_cannot_read_the_image) }
                        BitmapFactory.decodeStream(input, null, sourceDecodeOptions()) ?: error(getString(R.string.text_choose_a_valid_game_screenshot))
                    }
                    require(bitmap.width.toLong() * bitmap.height <= 32_000_000L) { getString(R.string.text_image_too_large) }
                    sourceFile.parentFile?.mkdirs()
                    sourceFile.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
                loadSource()
            }.onFailure { status.text = it.message ?: getString(R.string.text_screenshot_import_failed) }
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
        button(getString(R.string.text_back)) { finish() }
        button(getString(R.string.text_import_unfiltered_screenshot)) { picker.launch("image/*") }
        originalButton = button(getString(R.string.text_show_original_x)) { toggleOriginal() }
        button(getString(R.string.text_live_integration_status)) { showRuntimeInfo() }
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
        if (bitmap == null) status.text = getString(R.string.text_import_a_game_screenshot_with_all_filters)
        else {
            preview.setImage(bitmap)
            status.text = getString(R.string.text_value_value_source_value_screen_resolution_preview, bitmap.width, bitmap.height, effects.family)
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
        originalButton.text = if (showOriginal) getString(R.string.text_show_filter_x) else getString(R.string.text_show_original_x)
    }

    private fun showRuntimeInfo() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.text_live_rendering_integration))
            .setMessage(getString(R.string.text_this_preview_processes_your_imported_screenshot_on))
            .setPositiveButton(getString(R.string.text_got_it), null).show()
    }

    // core 1.13.1 restricts its base class; this is the public Activity callback.
    // Preserve normal dispatch for keys not consumed by the launcher.
    @Suppress("RestrictedApi")
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
