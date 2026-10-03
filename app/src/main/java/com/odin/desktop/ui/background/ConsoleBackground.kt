package com.odin.desktop.ui.background

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.odin.desktop.ui.theme.LocalOdinPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.sin

/** Low-cost, decorative console backdrop. The parent controls screen and modal visibility. */
@Composable
fun ConsoleBackground(modifier: Modifier = Modifier, motionEnabled: Boolean = true) {
    val palette = LocalOdinPalette.current
    val allowed = rememberBackgroundMotionAllowed(motionEnabled)
    val phase = remember { mutableFloatStateOf(0f) }
    val clock = remember { BackgroundMotionClock() }

    LaunchedEffect(allowed) {
        if (!allowed) return@LaunchedEffect
        try {
            // Twenty updates per second suffice for this 48-second, small-displacement motion.
            withFrameNanos { clock.advance(it) }
            while (coroutineContext.isActive) {
                delay(50L)
                withFrameNanos { phase.floatValue = clock.advance(it) }
            }
        } finally {
            clock.pause()
        }
    }

    Spacer(
        modifier = modifier.fillMaxSize().drawWithCache {
            val width = size.width
            val height = size.height
            val base = lerp(palette.background, Color(0xFF030A19), 0.78f)
            val depthBrush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0x39091D3B)),
                startY = height * 0.24f,
                endY = height
            )
            val far = ribbon(size, .66f, .58f, .72f, .77f, .90f, .83f, .92f, 1.02f)
            val middle = ribbon(size, .82f, .72f, .68f, .79f, 1.01f, .93f, .89f, 1.04f)
            val near = ribbon(size, .96f, .85f, .78f, .87f, 1.12f, 1.02f, .97f, 1.12f)
            val glint = ribbon(size, .91f, .88f, .77f, .83f, .935f, .90f, .80f, .86f)
            val gradientHeight = height.coerceAtLeast(1f)
            val farBrush = ribbonBrush(gradientHeight, Color(0x00265AA0), Color(0x52265AA0), Color(0x09142C59))
            val middleBrush = ribbonBrush(gradientHeight, Color(0x002767BE), Color(0x70407DD0), Color(0x14113265))
            val nearBrush = ribbonBrush(gradientHeight, Color(0x003C76C0), Color(0x665187DB), Color(0x1A112D61))
            val glintBrush = ribbonBrush(gradientHeight, Color(0x002A6DCE), Color(0x483C91EC), Color(0x001547A0))
            val hairline = Stroke(width = (width * .0010f).coerceIn(1f, 2.4f))

            onDrawBehind {
                drawRect(base)
                drawRect(depthBrush)
                if (width <= 0f || height <= 0f) return@onDrawBehind

                // Snapshot state is read only here. This invalidates the draw node, not the UI tree.
                val cycle = phase.floatValue * (2f * Math.PI.toFloat())
                clipRect {
                    translate(
                        left = width * .014f * sin(cycle),
                        top = height * .006f * sin(cycle + .7f)
                    ) {
                        drawPath(far.fill, farBrush)
                        drawPath(far.crest, Color(0x194478BD), style = hairline)
                    }
                    translate(
                        left = width * .018f * sin(cycle + 1.9f),
                        top = height * .007f * sin(cycle + 2.4f)
                    ) {
                        drawPath(middle.fill, middleBrush)
                        drawPath(middle.crest, Color(0x386BA7E5), style = hairline)
                    }
                    translate(
                        left = width * .011f * sin(cycle + 3.5f),
                        top = height * .008f * sin(cycle + 1.5f)
                    ) {
                        drawPath(near.fill, nearBrush)
                    }
                    translate(
                        left = width * .013f * sin(cycle + 4.7f),
                        top = height * .005f * sin(cycle + 3.3f)
                    ) {
                        drawPath(glint.fill, glintBrush)
                        drawPath(glint.crest, Color(0x455CA9EE), style = hairline)
                    }
                }
            }
        }
    )
}

private data class Ribbon(val fill: Path, val crest: Path)

/** All geometry is built once per size change, then reused by every draw. */
private fun ribbon(
    size: Size,
    start: Float, control1: Float, control2: Float, end: Float,
    bottomStart: Float, bottomControl1: Float, bottomControl2: Float, bottomEnd: Float
): Ribbon {
    val w = size.width
    val h = size.height
    val left = -.20f * w
    val right = 1.20f * w
    val crest = Path().apply {
        moveTo(left, start * h)
        cubicTo(.16f * w, control1 * h, .72f * w, control2 * h, right, end * h)
    }
    val fill = Path().apply {
        addPath(crest)
        lineTo(right, bottomEnd * h)
        cubicTo(.72f * w, bottomControl2 * h, .16f * w, bottomControl1 * h, left, bottomStart * h)
        close()
    }
    return Ribbon(fill, crest)
}

private fun ribbonBrush(height: Float, top: Color, middle: Color, bottom: Color): Brush =
    Brush.verticalGradient(
        colors = listOf(top, middle, bottom),
        startY = height * .57f,
        endY = height * 1.10f
    )
