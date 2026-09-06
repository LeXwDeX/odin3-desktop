package com.odin.desktop.shader.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.net.Uri
import android.util.AtomicFile
import com.odin.desktop.R
import java.io.File
import java.io.InputStream

/** Shared, bounded screenshot import for both preview entry points. Call on an IO dispatcher. */
object ShaderSourceImage {
    private fun decode(context: Context, open: () -> InputStream?): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            context.getString(R.string.text_choose_a_valid_screenshot)
        }
        require(bounds.outWidth.toLong() * bounds.outHeight <= 32_000_000L) {
            context.getString(R.string.text_screenshot_too_large)
        }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        return open().use { BitmapFactory.decodeStream(it, null, options) }
            ?: error(context.getString(R.string.text_choose_a_valid_screenshot))
    }

    @Synchronized fun read(context: Context, file: File): Bitmap? =
        runCatching { decode(context) { AtomicFile(file).openRead() } }.getOrNull()

    @Synchronized fun import(context: Context, uri: Uri, destination: File): Bitmap {
        val bitmap = decode(context) { context.contentResolver.openInputStream(uri) }
        try {
            destination.parentFile?.mkdirs()
            val file = AtomicFile(destination)
            val output = file.startWrite()
            try {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    context.getString(R.string.text_screenshot_import_failed)
                }
                file.finishWrite(output)
            } catch (error: Throwable) {
                file.failWrite(output)
                throw error
            }
            return bitmap
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    }
}
