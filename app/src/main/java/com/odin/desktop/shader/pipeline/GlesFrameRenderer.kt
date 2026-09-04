package com.odin.desktop.shader.pipeline

/**
 * Processes a texture supplied by the host, on its owning GLES thread and EGL context.
 * The input is a GL_TEXTURE_2D with bottom-left UVs and sRGB-encoded opaque RGB.
 * The destination framebuffer must not contain the input texture. The host retains
 * ownership of both; release only frees renderer-owned resources on the same context.
 * Compilation and framebuffer errors propagate to the host. This does not acquire game frames.
 */
interface GlesFrameRenderer<in Settings> {
    fun render(
        inputTextureId: Int,
        inputWidth: Int,
        inputHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
        settings: Settings,
        timeSeconds: Float,
        framebuffer: Int = 0
    )

    fun release()
}
