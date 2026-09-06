package com.odin.desktop.shader

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import com.odin.desktop.R
import com.odin.desktop.shader.preview.ShaderSourceImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShaderSourceImageTest {
    @Test fun oversizedHeaderIsRejectedBeforeDecodingAndPreservesPreviousSource() {
        val app = RuntimeEnvironment.getApplication()
        val bytes = ByteArrayOutputStream().also { output ->
            Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).also {
                it.compress(Bitmap.CompressFormat.PNG, 100, output)
                it.recycle()
            }
        }.toByteArray()
        // Rewrite the PNG IHDR and its CRC without allocating a large bitmap.
        ByteBuffer.wrap(bytes).putInt(16, 10_000).putInt(20, 10_000)
        val crc = CRC32().apply { update(bytes, 12, 17) }.value.toInt()
        ByteBuffer.wrap(bytes).putInt(29, crc)
        val input = File(app.cacheDir, "oversized.png").apply { writeBytes(bytes) }
        val destination = File(app.filesDir, "source.png").apply { writeText("previous screenshot") }
        val error = runCatching { ShaderSourceImage.import(app, Uri.fromFile(input), destination) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(app.getString(R.string.text_screenshot_too_large), error?.message)
        assertEquals("previous screenshot", destination.readText())
    }

    @Test fun validImageRoundTripsAndCorruptInputDoesNotReplaceIt() {
        val app = RuntimeEnvironment.getApplication()
        val input = File(app.cacheDir, "valid.png")
        Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(android.graphics.Color.RED)
            input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        val destination = File(app.filesDir, "source.png")
        ShaderSourceImage.import(app, Uri.fromFile(input), destination).recycle()
        val before = destination.readBytes()
        val loaded = ShaderSourceImage.read(app, destination)!!
        assertEquals(32, loaded.width)
        assertEquals(android.graphics.Color.RED, loaded.getPixel(2, 2))
        loaded.recycle()
        input.writeText("not an image")
        assertTrue(runCatching { ShaderSourceImage.import(app, Uri.fromFile(input), destination) }.isFailure)
        assertArrayEquals(before, destination.readBytes())
    }
}
