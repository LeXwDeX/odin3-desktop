package com.odin.desktop.shader.gl

import android.content.Context
import android.opengl.EGL14
import android.opengl.GLES30
import com.odin.desktop.shader.model.GameNativeShaderSettings
import com.odin.desktop.shader.model.ShaderFamily
import com.odin.desktop.shader.model.ShaderScaling
import kotlin.math.abs

/**
 * GameNative's two shader families, executed on an already current GLES 3 context.
 *
 * The caller owns the EGL context, input GL_TEXTURE_2D and destination framebuffer. Texture
 * coordinates use OpenGL's bottom-left origin; a top-first Bitmap upload must be flipped by the
 * caller before use. The renderer never reads pixels back or changes the input texture's filters.
 * Output dimensions are physical screen pixels and set every screen-effect resolution uniform.
 *
 * Construct, render and release on the same thread with the same EGL context current. This class
 * owns its GL state during rendering: it changes framebuffer, viewport, program, VAO, texture unit
 * zero, sampler and raster state. Callers sharing the context must rebind their state afterwards.
 * It does not hook another process, acquire its frames, or implement Vulkan command submission;
 * VULKAN selects GameNative's Vulkan shader equations, compiled for GLES for image comparisons.
 */
class GameNativeGlRenderer(context: Context) : com.odin.desktop.shader.pipeline.GlesFrameRenderer<GameNativeShaderSettings> {
    private val assets = context.applicationContext.assets
    private val ownerThread = Thread.currentThread().id
    private val ownerContext = EGL14.eglGetCurrentContext()
    private val programs = mutableMapOf<String, Program>()
    private val samplers = IntArray(2)
    private val vertexArrays = IntArray(1)
    private var targets = emptyList<Target>()
    private var targetWidth = 0
    private var targetHeight = 0
    private var released = false
    private val maximumTextureSize: Int
    private var cachedSettings: GameNativeShaderSettings? = null
    private var cachedPasses = emptyList<String>()

    init {
        check(ownerContext != EGL14.EGL_NO_CONTEXT) { "GameNative shader requires a current EGL context" }
        val major = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAJOR_VERSION, major, 0)
        check(major[0] >= 3) { "GameNative shader requires OpenGL ES 3.0 or newer" }
        val maxSize = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxSize, 0)
        maximumTextureSize = maxSize[0]
        try {
            GLES30.glGenVertexArrays(1, vertexArrays, 0)
            GLES30.glGenSamplers(2, samplers, 0)
            samplers.forEachIndexed { index, sampler ->
                val filter = if (index == 0) GLES30.GL_LINEAR else GLES30.GL_NEAREST
                GLES30.glSamplerParameteri(sampler, GLES30.GL_TEXTURE_MIN_FILTER, filter)
                GLES30.glSamplerParameteri(sampler, GLES30.GL_TEXTURE_MAG_FILTER, filter)
                GLES30.glSamplerParameteri(sampler, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
                GLES30.glSamplerParameteri(sampler, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            }
            checkGl("initializing renderer")
        } catch (failure: Throwable) {
            GLES30.glDeleteSamplers(samplers.size, samplers, 0)
            GLES30.glDeleteVertexArrays(vertexArrays.size, vertexArrays, 0)
            throw failure
        }
    }

    /**
     * Renders normalized opaque RGB. [timeSeconds] selects the GL NTSC phase at a 60 Hz reference
     * cadence; pass the same time for reproducible comparisons. A destination must not sample its
     * own attached input texture. Shader compile/link and framebuffer errors are reported to callers.
     */
    override fun render(
        inputTextureId: Int,
        inputWidth: Int,
        inputHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
        settings: GameNativeShaderSettings,
        timeSeconds: Float,
        framebuffer: Int
    ) {
        checkCurrentContext()
        require(inputTextureId > 0 && GLES30.glIsTexture(inputTextureId)) { "Shader input must be an existing 2D texture" }
        require(inputWidth in 1..maximumTextureSize && inputHeight in 1..maximumTextureSize) {
            "Invalid shader source size $inputWidth x $inputHeight (limit $maximumTextureSize)"
        }
        require(outputWidth in 1..maximumTextureSize && outputHeight in 1..maximumTextureSize) {
            "Invalid shader output size $outputWidth x $outputHeight (limit $maximumTextureSize)"
        }
        require(framebuffer >= 0) { "Invalid output framebuffer" }
        val normalized = settings.normalized()
        val passes = passesFor(normalized)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        if (framebuffer != 0) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
            val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
            check(status == GLES30.GL_FRAMEBUFFER_COMPLETE) {
                "Output framebuffer incomplete: 0x${status.toString(16)}"
            }
            val attachment = IntArray(1)
            GLES30.glGetFramebufferAttachmentParameteriv(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE, attachment, 0
            )
            if (attachment[0] == GLES30.GL_TEXTURE) {
                GLES30.glGetFramebufferAttachmentParameteriv(
                    GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                    GLES30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME, attachment, 0
                )
                require(attachment[0] != inputTextureId) { "Shader output framebuffer must not overwrite the input texture" }
            }
        }
        if (passes.size > 1) ensureTargets(outputWidth, outputHeight)

        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_STENCIL_TEST)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_DITHER)
        GLES30.glDisable(GLES30.GL_RASTERIZER_DISCARD)
        GLES30.glDisable(GLES30.GL_SAMPLE_ALPHA_TO_COVERAGE)
        GLES30.glDisable(GLES30.GL_SAMPLE_COVERAGE)
        GLES30.glColorMask(true, true, true, true)
        GLES30.glBindVertexArray(vertexArrays[0])
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

        var texture = inputTextureId
        var width = inputWidth
        var height = inputHeight
        try {
            passes.forEachIndexed { index, asset ->
                val last = index == passes.lastIndex
                val target = if (last) null else targets[index % targets.size]
                val destination = target?.framebuffer ?: framebuffer
                require(target?.texture != texture) { "Shader cannot sample its own output texture" }
                val program = program(asset)
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, destination)
                GLES30.glViewport(0, 0, outputWidth, outputHeight)
                GLES30.glUseProgram(program.id)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
                // EffectComposer resets every GL source to NEAREST, except ScalingModeEffect's
                // explicit LINEAR/FILL/STRETCH override. FSR, FXAA and CRT also use NEAREST there.
                val nearest = when {
                    normalized.family == ShaderFamily.VULKAN -> normalized.scaling == ShaderScaling.NEAREST
                    asset == "opengl/ScalingModeEffect.frag" ->
                        normalized.scaling == ShaderScaling.NEAREST || normalized.scaling == ShaderScaling.NONE
                    else -> true
                }
                GLES30.glBindSampler(0, samplers[if (nearest) 1 else 0])
                setUniforms(program, width, height, outputWidth, outputHeight, normalized, timeSeconds)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
                checkGl("rendering $asset")
                if (target != null) {
                    texture = target.texture
                    width = outputWidth
                    height = outputHeight
                }
            }
        } finally {
            GLES30.glBindSampler(0, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            GLES30.glBindVertexArray(0)
            GLES30.glUseProgram(0)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        }
    }

    override fun release() {
        if (released) return
        checkCurrentContext()
        programs.values.forEach { GLES30.glDeleteProgram(it.id) }
        programs.clear()
        targets.forEach { it.release() }
        targets = emptyList()
        GLES30.glDeleteSamplers(samplers.size, samplers, 0)
        GLES30.glDeleteVertexArrays(vertexArrays.size, vertexArrays, 0)
        released = true
    }

    private fun checkCurrentContext() {
        check(!released) { "GameNative shader renderer has been released" }
        check(Thread.currentThread().id == ownerThread && EGL14.eglGetCurrentContext() == ownerContext) {
            "GameNative shader must run on its owning GL thread and EGL context"
        }
    }

    private fun passesFor(settings: GameNativeShaderSettings): List<String> {
        if (cachedSettings == settings) return cachedPasses
        cachedPasses = if (settings.family == ShaderFamily.VULKAN) {
            // FXAA and NTSC deliberately re-read the source in this single pass, as upstream does.
            listOf("vulkan/window.frag")
        } else {
            buildList {
                when (settings.scaling) {
                    ShaderScaling.FSR, ShaderScaling.FSR_ASPECT -> {
                        add("opengl/FSR1EasuEffect.frag")
                        add("opengl/FSR1RcasEffect.frag")
                    }
                    else -> add("opengl/ScalingModeEffect.frag")
                }
                if (abs(settings.brightness) > 0.001f || abs(settings.contrast) > 0.001f ||
                    abs(settings.gamma - 1f) > 0.001f
                ) add("opengl/ColorEffect.frag")
                if (settings.enableToon) add("opengl/ToonEffect.frag")
                if (settings.enableFXAA) add("opengl/FXAAEffect.frag")
                if (settings.enableVivid) add("opengl/VividEffect.frag")
                if (settings.enableCRT) add("opengl/CRTEffect.frag")
                if (settings.enableNTSC) add("opengl/NTSCCombinedEffect.frag")
            }
        }
        cachedSettings = settings
        return cachedPasses
    }

    private fun setUniforms(
        program: Program,
        inputWidth: Int,
        inputHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
        settings: GameNativeShaderSettings,
        timeSeconds: Float
    ) {
        val outW = outputWidth.toFloat()
        val outH = outputHeight.toFloat()
        program.int("screenTexture", 0)
        program.int("texSampler", 0)
        program.vec2("inputResolution", inputWidth.toFloat(), inputHeight.toFloat())
        program.vec2("outputResolution", outW, outH)
        program.vec2("resolution", outW, outH)
        program.vec2("TextureSize", outW, outH)
        program.float("scaleMode", when (settings.scaling) {
            ShaderScaling.FILL -> 1f
            ShaderScaling.STRETCH, ShaderScaling.FSR -> 2f
            else -> 0f
        })
        program.float("preserveAspect", if (settings.scaling == ShaderScaling.FSR_ASPECT) 1f else 0f)
        program.float("sharpnessStops", (5 - settings.fsrSharpnessLevel) * 0.5f)
        program.float("brightness", settings.brightness / 100f)
        program.float("contrast", settings.contrast / 100f)
        program.float("gamma", settings.gamma)
        val time = if (timeSeconds.isFinite()) timeSeconds.coerceAtLeast(0f).toDouble() else 0.0
        program.int("FrameCount", ((time * 60.0).toLong() % 4L).toInt())
        program.int("pc.useTexAlpha", 0)
        program.int("pc.effectId", when (settings.scaling) {
            ShaderScaling.FSR, ShaderScaling.FSR_ASPECT -> 1
            ShaderScaling.DLS -> 2
            ShaderScaling.NATURAL -> 5
            else -> 0
        })
        program.float("pc.sharpness", (settings.fsrSharpnessLevel - 1) / 4f)
        program.float("pc.resW", outW)
        program.float("pc.resH", outH)
        program.float("pc.outW", outW)
        program.float("pc.outH", outH)
        program.float("pc.brightness", settings.brightness / 100f)
        program.float("pc.contrast", settings.contrast / 100f)
        program.float("pc.gamma", settings.gamma)
        program.int("pc.effectMask",
            (if (settings.enableToon) 1 else 0) or
                (if (settings.enableFXAA) 2 else 0) or
                (if (settings.enableVivid) 4 else 0) or
                (if (settings.enableCRT) 8 else 0) or
                (if (settings.enableNTSC) 16 else 0)
        )
    }

    private fun ensureTargets(width: Int, height: Int) {
        if (targets.isNotEmpty() && width == targetWidth && height == targetHeight) return
        // Release the old-size buffers first so orientation changes do not temporarily double VRAM.
        targets.forEach { it.release() }
        targets = emptyList()
        val allocated = mutableListOf<Target>()
        try {
            repeat(2) { allocated += Target.create(width, height) }
            targets = allocated
            targetWidth = width
            targetHeight = height
        } catch (failure: Throwable) {
            allocated.forEach { it.release() }
            throw failure
        }
    }

    private fun program(asset: String): Program = programs.getOrPut(asset) {
        val vertex = compile(GLES30.GL_VERTEX_SHADER, readAsset("fullscreen.vert"), "fullscreen.vert")
        var fragment = 0
        var id = 0
        try {
            fragment = compile(GLES30.GL_FRAGMENT_SHADER, readAsset(asset), asset)
            id = GLES30.glCreateProgram()
            check(id != 0) { "Cannot allocate shader program for $asset" }
            GLES30.glAttachShader(id, vertex)
            GLES30.glAttachShader(id, fragment)
            GLES30.glLinkProgram(id)
            val status = IntArray(1)
            GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES30.GL_TRUE) { "Cannot link $asset: ${GLES30.glGetProgramInfoLog(id)}" }
            Program(id)
        } catch (failure: Throwable) {
            if (id != 0) GLES30.glDeleteProgram(id)
            throw failure
        } finally {
            GLES30.glDeleteShader(vertex)
            if (fragment != 0) GLES30.glDeleteShader(fragment)
        }
    }

    private fun readAsset(path: String): String = assets.open("shaders/gamenative/$path").bufferedReader().use { it.readText() }

    private fun compile(type: Int, source: String, name: String): Int {
        val id = GLES30.glCreateShader(type)
        check(id != 0) { "Cannot allocate shader $name" }
        GLES30.glShaderSource(id, source)
        GLES30.glCompileShader(id)
        val status = IntArray(1)
        GLES30.glGetShaderiv(id, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES30.GL_TRUE) {
            val message = GLES30.glGetShaderInfoLog(id)
            GLES30.glDeleteShader(id)
            error("Cannot compile $name: $message")
        }
        return id
    }

    private class Program(val id: Int) {
        private val uniforms = mutableMapOf<String, Int>()
        private fun location(name: String): Int = uniforms.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }
        fun int(name: String, value: Int) {
            val location = location(name)
            if (location >= 0) GLES30.glUniform1i(location, value)
        }
        fun float(name: String, value: Float) {
            val location = location(name)
            if (location >= 0) GLES30.glUniform1f(location, value)
        }
        fun vec2(name: String, x: Float, y: Float) {
            val location = location(name)
            if (location >= 0) GLES30.glUniform2f(location, x, y)
        }
    }

    private data class Target(val texture: Int, val framebuffer: Int) {
        fun release() {
            GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
            GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
        }

        companion object {
            fun create(width: Int, height: Int): Target {
                val texture = IntArray(1)
                val framebuffer = IntArray(1)
                try {
                    GLES30.glGenTextures(1, texture, 0)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0])
                    GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_RGBA8, width, height)
                    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
                    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
                    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
                    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
                    GLES30.glGenFramebuffers(1, framebuffer, 0)
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer[0])
                    GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texture[0], 0)
                    val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
                    check(status == GLES30.GL_FRAMEBUFFER_COMPLETE) { "Shader framebuffer incomplete: 0x${status.toString(16)}" }
                    checkGl("allocating $width x $height shader framebuffer")
                    return Target(texture[0], framebuffer[0])
                } catch (failure: Throwable) {
                    GLES30.glDeleteFramebuffers(1, framebuffer, 0)
                    GLES30.glDeleteTextures(1, texture, 0)
                    throw failure
                }
            }
        }
    }

    companion object {
        private fun checkGl(operation: String) {
            val error = GLES30.glGetError()
            check(error == GLES30.GL_NO_ERROR) { "OpenGL error 0x${error.toString(16)} while $operation" }
        }
    }
}
