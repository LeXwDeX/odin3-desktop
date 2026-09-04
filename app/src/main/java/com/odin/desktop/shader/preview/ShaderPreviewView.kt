package com.odin.desktop.shader.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.GLES30 as GL
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import com.odin.desktop.shader.gl.GameNativeGlRenderer
import com.odin.desktop.shader.model.GameNativeShaderSettings
import com.odin.desktop.shader.model.ShaderFamily
import com.odin.desktop.shader.model.ShaderScaling
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Full-screen GPU preview. Validation exports are available only in debug builds. */
class ShaderPreviewView(context: Context, private val report: (String) -> Unit) : GLSurfaceView(context) {
    private val renderer = PreviewRenderer()
    init {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setEffects(value: GameNativeShaderSettings) { queueEvent { renderer.effects = value.normalized() }; requestRender() }
    fun showOriginal(value: Boolean) { queueEvent { renderer.original = value }; requestRender() }
    fun setImage(value: Bitmap) {
        queueEvent { renderer.source?.recycle(); renderer.source = value; renderer.upload = true }
        requestRender()
    }
    fun runValidationSuite() { queueEvent { renderer.validate = true }; requestRender() }

    private inner class PreviewRenderer : Renderer {
        var effects = GameNativeShaderSettings()
        var original = false
        var source: Bitmap? = null
        var upload = false
        var validate = false
        private var gpu: GameNativeGlRenderer? = null
        private var texture = 0
        private var width = 0
        private var height = 0
        private var failed = false
        private val neutral = GameNativeShaderSettings(enableCRT = false)
        private val originalSettings = neutral.copy(scaling = ShaderScaling.NEAREST)

        override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
            gpu = GameNativeGlRenderer(context)
            texture = 0
            upload = source != null
            failed = false
        }

        override fun onSurfaceChanged(unused: GL10?, w: Int, h: Int) { width = w; height = h }

        override fun onDrawFrame(unused: GL10?) {
            if (failed || width <= 0 || height <= 0) return
            GL.glViewport(0, 0, width, height)
            GL.glClearColor(0f, 0f, 0f, 1f)
            GL.glClear(GL.GL_COLOR_BUFFER_BIT)
            val bitmap = source ?: return
            try {
                if (upload) {
                    if (texture != 0) GL.glDeleteTextures(1, intArrayOf(texture), 0)
                    val names = IntArray(1)
                    GL.glGenTextures(1, names, 0)
                    texture = names[0]
                    GL.glBindTexture(GL.GL_TEXTURE_2D, texture)
                    GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR)
                    GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR)
                    GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE)
                    GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE)
                    // Bitmap rows start at the top; the shared renderer uses bottom-left texture UVs.
                    val flipped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { preScale(1f, -1f) }, false)
                    GLUtils.texImage2D(GL.GL_TEXTURE_2D, 0, flipped, 0)
                    if (flipped !== bitmap) flipped.recycle()
                    upload = false
                }
                val engine = gpu ?: return
                if (validate) {
                    validate = false
                    validate(engine, bitmap)
                }
                val value = if (original) originalSettings else effects
                engine.render(texture, bitmap.width, bitmap.height, width, height, value, android.os.SystemClock.uptimeMillis() / 1000f)
                val error = GL.glGetError()
                check(error == GL.GL_NO_ERROR) { "GPU 错误 0x${error.toString(16)}" }
                if (value.enableNTSC && value.family == ShaderFamily.OPENGL) requestRender()
            } catch (error: Exception) {
                failed = true
                report("滤镜渲染失败：${error.message}")
                android.util.Log.e("ShaderPreview", "Rendering failed", error)
            }
        }

        private fun pixels(): IntArray {
            val data = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
            GL.glReadPixels(0, 0, width, height, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, data)
            val result = IntArray(width * height)
            for (y in 0 until height) for (x in 0 until width) {
                val offset = ((height - 1 - y) * width + x) * 4
                result[y * width + x] = android.graphics.Color.argb(
                    data.get(offset + 3).toInt() and 255, data.get(offset).toInt() and 255,
                    data.get(offset + 1).toInt() and 255, data.get(offset + 2).toInt() and 255
                )
            }
            return result
        }

        private fun validate(engine: GameNativeGlRenderer, bitmap: Bitmap) {
            val dir = File(context.filesDir, "shader_preview/results").apply { mkdirs() }
            File(dir, "source-srgb.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val cases = linkedMapOf("original" to originalSettings)
            for (family in ShaderFamily.entries) {
                val base = neutral.copy(family = family)
                for (mode in base.availableScalingModes) cases["${family.name.lowercase()}_${mode.name.lowercase()}"] = base.copy(scaling = mode)
                cases["${family.name.lowercase()}_crt"] = base.copy(enableCRT = true)
                cases["${family.name.lowercase()}_fxaa"] = base.copy(enableFXAA = true)
                cases["${family.name.lowercase()}_vivid"] = base.copy(enableVivid = true)
                cases["${family.name.lowercase()}_toon"] = base.copy(enableToon = true)
                cases["${family.name.lowercase()}_ntsc"] = base.copy(enableNTSC = true)
                cases["${family.name.lowercase()}_color"] = base.copy(brightness = 10f, contrast = 10f, gamma = 1.2f)
                cases["${family.name.lowercase()}_combined"] = base.copy(enableCRT = true, enableFXAA = true, enableVivid = true, enableToon = true, enableNTSC = true)
            }
            var reference: IntArray? = null
            val results = JSONArray()
            for ((name, config) in cases) {
                val result = JSONObject().put("name", name).put("settings", JSONObject(config.toJson()))
                try {
                    engine.render(texture, bitmap.width, bitmap.height, width, height, config, 0.5f)
                    GL.glFinish()
                    check(GL.glGetError() == GL.GL_NO_ERROR) { "OpenGL error" }
                    val output = pixels()
                    if (name == "original" && width == bitmap.width && height == bitmap.height) {
                        val expected = IntArray(width * height)
                        bitmap.getPixels(expected, 0, width, 0, 0, width, height)
                        val mismatches = output.indices.count { output[it] != expected[it] }
                        result.put("originalMismatches", mismatches)
                        check(mismatches == 0) { "Original image changed at $mismatches pixels" }
                    }
                    if (reference == null) reference = output
                    val changed = output.indices.count { output[it] != reference!![it] }
                    val image = Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
                    File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    image.recycle()
                    result.put("status", "rendered").put("changedPixels", changed).put("width", width).put("height", height)
                } catch (error: Exception) {
                    result.put("status", "failed").put("error", error.message)
                }
                results.put(result)
            }
            File(dir, "results.json").writeText(JSONObject()
                .put("scope", "GPU rendering and image comparison; not live-game hook or frame-rate validation")
                .put("colorSpace", bitmap.colorSpace?.name)
                .put("renderer", GL.glGetString(GL.GL_RENDERER)).put("cases", results).toString(2))
            report("GPU 对照已导出 ${results.length()} 组；X 对比当前设置与原图。")
            android.util.Log.i("ShaderPreview", "Validation suite complete: ${results.length()} cases")
        }
    }
}
