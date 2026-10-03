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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.odin.desktop.ui.theme.LocalOdinPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

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
            // Twenty updates per second suffice for this 24-second eased motion.
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
                colors = listOf(Color.Transparent, Color(0x35091D3B)),
                startY = height * .15f,
                endY = height * .85f
            )
            // Crests cross the middle of the viewport; the geometry stays cached.
            val far = ribbon(size, .34f, .30f, .42f, .42f, .53f, .48f, .60f, .59f)
            val middle = ribbon(size, .48f, .41f, .44f, .51f, .66f, .58f, .61f, .68f)
            val near = ribbon(size, .62f, .55f, .52f, .60f, .77f, .71f, .69f, .78f)
            val glint = ribbon(size, .50f, .43f, .45f, .51f, .54f, .47f, .49f, .55f)
            val gradientHeight = height.coerceAtLeast(1f)
            val farBrush = ribbonBrush(gradientHeight, Color(0x00265AA0), Color(0x46265AA0), Color(0x09142C59))
            val middleBrush = ribbonBrush(gradientHeight, Color(0x002767BE), Color(0x70407DD0), Color(0x14113265))
            val nearBrush = ribbonBrush(gradientHeight, Color(0x003C76C0), Color(0x5C5187DB), Color(0x10112D61))
            val glintBrush = Brush.verticalGradient(
                colors = listOf(Color(0x002A6DCE), Color(0x684B9DF2), Color(0x001547A0)),
                startY = gradientHeight * .41f,
                endY = gradientHeight * .59f
            )
            val hairline = Stroke(width = (width * .0010f).coerceIn(1f, 2.4f))

            onDrawBehind {
                drawRect(base)
                drawRect(depthBrush)
                if (width <= 0f || height <= 0f) return@onDrawBehind

                // Snapshot state is read only here. This invalidates the draw node, not the UI tree.
                val cycle = phase.floatValue
                val farMotion = easedWave(cycle + .10f)
                val middleMotion = easedWave(cycle)
                val nearMotion = easedWave(cycle + .36f)
                val glintMotion = easedWave(cycle + .19f)
                clipRect {
                    translate(
                        left = width * .012f * farMotion,
                        top = height * .030f * farMotion
                    ) {
                        scale(scaleX = 1f + .008f * farMotion, scaleY = 1f + .030f * farMotion) {
                            drawPath(far.fill, farBrush)
                            drawPath(far.crest, Color(0x284478BD), style = hairline)
                        }
                    }
                    translate(
                        left = width * .020f * middleMotion,
                        top = height * .040f * middleMotion
                    ) {
                        scale(scaleX = 1f + .010f * middleMotion, scaleY = 1f + .045f * middleMotion) {
                            drawPath(middle.fill, middleBrush)
                            drawPath(middle.crest, Color(0x526BA7E5), style = hairline)
                        }
                    }
                    translate(
                        left = width * .014f * nearMotion,
                        top = height * .035f * nearMotion
                    ) {
                        scale(scaleX = 1f + .008f * nearMotion, scaleY = 1f + .036f * nearMotion) {
                            drawPath(near.fill, nearBrush)
                        }
                    }
                    translate(
                        left = width * .018f * glintMotion,
                        top = height * .045f * glintMotion
                    ) {
                        scale(scaleX = 1f + .008f * glintMotion, scaleY = 1f + .035f * glintMotion) {
                            drawPath(glint.fill, glintBrush)
                            drawPath(glint.crest, Color(0x685CA9EE), style = hairline)
                        }
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
        startY = height * .28f,
        endY = height * .77f
    )
