package com.odin.desktop.data.repository

import android.app.Application
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LevelListDrawable
import android.graphics.drawable.VectorDrawable
import com.odin.desktop.R
import java.util.concurrent.CancellationException
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
class AppIconPreparationTest {
    private val resources = RuntimeEnvironment.getApplication().resources

    @Test fun largeBitmapPreservesAspectTransparencyAndOriginalBounds() {
        val sourceBitmap = bitmap(800, 400)
        Canvas(sourceBitmap).drawRect(400f, 0f, 800f, 400f, Paint().apply {
            color = Color.argb(128, 255, 0, 0)
        })
        val source = BitmapDrawable(resources, sourceBitmap).apply { setBounds(4, 7, 804, 407) }
        val bounds = Rect(source.bounds)

        val prepared = AppIconPreparation.prepare(source, resources) as BitmapDrawable

        assertEquals(256, prepared.bitmap.width)
        assertEquals(128, prepared.bitmap.height)
        assertEquals(resources.displayMetrics.densityDpi, prepared.bitmap.density)
        assertEquals(256, prepared.intrinsicWidth)
        assertEquals(128, prepared.intrinsicHeight)
        assertEquals(0, Color.alpha(prepared.bitmap.getPixel(32, 64)))
        assertEquals(Color.argb(128, 255, 0, 0), prepared.bitmap.getPixel(224, 64))
        assertEquals(bounds, source.bounds)
    }

    @Test fun smallBitmapIsNotEnlargedAndKeepsItsPixels() {
        val sourceBitmap = bitmap(48, 96).apply { eraseColor(Color.GREEN) }
        val source = BitmapDrawable(resources, sourceBitmap)

        val prepared = AppIconPreparation.prepare(source, resources) as BitmapDrawable

        assertEquals(48, prepared.bitmap.width)
        assertEquals(96, prepared.bitmap.height)
        assertTrue(sourceBitmap.sameAs(prepared.bitmap))
    }

    @Test fun lowDensityBitmapUsesDecodedPixelsWithoutUpscaling() {
        val sourceBitmap = bitmap(48, 96).apply {
            density = resources.displayMetrics.densityDpi / 2
            eraseColor(Color.GREEN)
        }
        val source = BitmapDrawable(resources, sourceBitmap)
        assertTrue(source.intrinsicWidth > sourceBitmap.width)

        val prepared = AppIconPreparation.prepare(source, resources) as BitmapDrawable

        assertEquals(48, prepared.bitmap.width)
        assertEquals(96, prepared.bitmap.height)
        assertTrue(sourceBitmap.sameAs(prepared.bitmap))
    }

    @Test fun vectorPreservesArtworkAndTransparentArea() {
        val source = (resources.getDrawable(R.drawable.ic_tile_afk, null) as VectorDrawable)
            .apply { setBounds(8, 9, 80, 90) }
        val bounds = Rect(source.bounds)
        val expected = render(source, source.intrinsicWidth, source.intrinsicHeight)

        val prepared = AppIconPreparation.prepare(source, resources) as BitmapDrawable

        assertTrue(expected.sameAs(prepared.bitmap))
        assertEquals(0, Color.alpha(prepared.bitmap.getPixel(0, 0)))
        assertEquals(Color.WHITE, prepared.bitmap.getPixel(prepared.bitmap.width / 2, prepared.bitmap.height / 2))
        assertEquals(bounds, source.bounds)
    }

    @Test fun adaptiveIconPreservesMaskLayersAndOriginalBounds() {
        val foreground = BitmapDrawable(resources, bitmap(512, 512).apply {
            Canvas(this).drawRect(192f, 192f, 320f, 320f, Paint().apply { color = Color.RED })
        })
        val source = AdaptiveIconDrawable(ColorDrawable(Color.BLUE), foreground).apply {
            setBounds(10, 20, 522, 532)
        }
        val bounds = Rect(source.bounds)
        val expected = render(source, 256, 256)

        val prepared = AppIconPreparation.prepare(source, resources) as BitmapDrawable

        assertEquals(256, prepared.bitmap.width)
        assertEquals(256, prepared.bitmap.height)
        assertTrue(expected.sameAs(prepared.bitmap))
        assertEquals(Color.RED, prepared.bitmap.getPixel(128, 128))
        assertEquals(bounds, source.bounds)
    }

    @Test fun drawFailureReturnsOriginalIconAndRestoresBounds() {
        val source = object : BitmapDrawable(resources, bitmap(300, 100)) {
            override fun draw(canvas: Canvas) = throw IllegalStateException("Vendor draw failure")
        }.apply { setBounds(5, 6, 305, 106) }
        val bounds = Rect(source.bounds)

        assertSame(source, AppIconPreparation.prepare(source, resources))
        assertEquals(bounds, source.bounds)
    }

    @Test fun statefulAnimatedAndLevelDependentIconsRemainLive() {
        val stateful = ColorDrawable(Color.RED).apply {
            setTintList(ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
                intArrayOf(Color.BLUE, Color.GREEN)
            ))
        }
        val animated = AnimationDrawable().apply { addFrame(ColorDrawable(Color.RED), 100) }
        val wrappedAnimation = InsetDrawable(animated, 4)
        val adaptiveAnimation = AdaptiveIconDrawable(ColorDrawable(Color.BLUE), animated)
        val levelDependent = LevelListDrawable().apply { addLevel(0, 1, ColorDrawable(Color.RED)) }

        for (source in listOf(stateful, animated, wrappedAnimation, adaptiveAnimation, levelDependent)) {
            assertSame(source, AppIconPreparation.prepare(source, resources))
        }
    }

    @Test fun cancellationAndFatalErrorsAreNotConvertedToFallbackIcons() {
        val cancelled = object : BitmapDrawable(resources, bitmap(16, 16)) {
            override fun draw(canvas: Canvas) = throw CancellationException("Cancelled scan")
        }
        val fatal = object : BitmapDrawable(resources, bitmap(16, 16)) {
            override fun draw(canvas: Canvas) = throw AssertionError("Fatal draw failure")
        }

        assertThrows(CancellationException::class.java) { AppIconPreparation.prepare(cancelled, resources) }
        assertThrows(AssertionError::class.java) { AppIconPreparation.prepare(fatal, resources) }
        assertEquals(Rect(), cancelled.bounds)
        assertEquals(Rect(), fatal.bounds)
    }

    private fun bitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            density = resources.displayMetrics.densityDpi
        }

    private fun render(icon: android.graphics.drawable.Drawable, width: Int, height: Int): Bitmap {
        val result = bitmap(width, height)
        val bounds = Rect(icon.bounds)
        try {
            icon.setBounds(0, 0, width, height)
            icon.draw(Canvas(result))
        } finally {
            icon.bounds = bounds
        }
        return result
    }
}
