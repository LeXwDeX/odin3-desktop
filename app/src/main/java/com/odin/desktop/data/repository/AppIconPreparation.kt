package com.odin.desktop.data.repository

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ScaleDrawable
import android.graphics.drawable.VectorDrawable
import java.util.concurrent.CancellationException
import kotlin.math.roundToInt

/** Prepares scan-owned icons on the repository's IO thread, before their first UI draw. */
internal object AppIconPreparation {
    // Covers the home strip's 72dp content at Odin 3 density without retaining oversized icons.
    private const val MAX_EDGE_PX = 256

    fun prepare(icon: Drawable, resources: Resources): Drawable {
        return try {
            // Keep changing icons live, including animations inside otherwise static wrappers.
            if (!isStaticIcon(icon)) return icon
            // A density-adjusted intrinsic size can exceed the decoded bitmap's actual pixels.
            val sourceWidth = (if (icon is BitmapDrawable) icon.bitmap.width else icon.intrinsicWidth)
                .takeIf { it > 0 } ?: MAX_EDGE_PX
            val sourceHeight = (if (icon is BitmapDrawable) icon.bitmap.height else icon.intrinsicHeight)
                .takeIf { it > 0 } ?: MAX_EDGE_PX
            val scale = minOf(1.0, MAX_EDGE_PX.toDouble() / maxOf(sourceWidth, sourceHeight))
            val width = (sourceWidth * scale).roundToInt().coerceIn(1, MAX_EDGE_PX)
            val height = (sourceHeight * scale).roundToInt().coerceIn(1, MAX_EDGE_PX)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                density = resources.displayMetrics.densityDpi
            }
            val originalBounds = Rect(icon.bounds)
            try {
                icon.setBounds(0, 0, width, height)
                icon.draw(Canvas(bitmap))
            } finally {
                icon.bounds = originalBounds
            }
            // On Android N+, this queues the GPU upload on RenderThread after our final draw.
            bitmap.prepareToDraw()
            BitmapDrawable(resources, bitmap)
        } catch (failure: RuntimeException) {
            if (failure is CancellationException) throw failure
            // One vendor drawable must not prevent its application from appearing in the scan.
            icon
        }
    }

    private fun isStaticIcon(icon: Drawable): Boolean {
        if (icon is Animatable || icon.isStateful) return false
        return when (icon) {
            is BitmapDrawable, is VectorDrawable, is ColorDrawable, is GradientDrawable -> true
            is AdaptiveIconDrawable -> isStaticChild(icon.background) && isStaticChild(icon.foreground)
            is LayerDrawable -> (0 until icon.numberOfLayers).all { isStaticChild(icon.getDrawable(it)) }
            is InsetDrawable -> isStaticChild(icon.drawable)
            is ScaleDrawable -> isStaticChild(icon.drawable)
            is ClipDrawable -> isStaticChild(icon.drawable)
            // Unknown drawables may change with level, time, or a vendor callback.
            else -> false
        }
    }

    private fun isStaticChild(icon: Drawable?): Boolean = icon == null || isStaticIcon(icon)
}
