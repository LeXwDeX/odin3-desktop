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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.odin.desktop.ui.theme.LocalOdinPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.max

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
            // Twenty updates per second suffice for this 24-second motion.
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
            val background = Brush.verticalGradient(
                colors = listOf(
                    lerp(palette.background, Color(0xFF102956), .70f),
                    lerp(palette.background, Color(0xFF111D4D), .76f),
                    lerp(palette.background, Color(0xFF060B22), .88f)
                ),
                startY = 0f,
                endY = height.coerceAtLeast(1f)
            )
            val softLight = Brush.radialGradient(
                colors = listOf(Color(0x264B80CD), Color(0x0B315DB0), Color.Transparent),
                center = Offset(width * .53f, height * .42f),
                radius = max(width, height).coerceAtLeast(1f) * .74f
            )
            val companionBrush = Brush.verticalGradient(
                colors = listOf(Color(0x00245B9C), Color(0x24396DB2), Color(0x001A3B78)),
                startY = height * .25f,
                endY = height * .62f
            )
            val ribbonBrush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x002C69B8),
                    Color(0x704982D0),
                    Color(0x244579C3),
                    Color(0x00172E67)
                ),
                startY = height * .28f,
                endY = height * .76f
            )
            // Geometry and shaders are rebuilt only if the viewport or palette changes.
            val companion = buildCompanion(width, height)
            val ribbon = buildRibbon(width, height)

            onDrawBehind {
                drawRect(background)
                drawRect(softLight)
                if (width <= 0f || height <= 0f) return@onDrawBehind

                // Snapshot state is read only here. This invalidates the draw node, not the UI tree.
                val motion = easedWave(phase.floatValue)
                clipRect {
                    translate(left = width * .024f * motion, top = height * .045f * motion) {
                        scale(scaleX = 1f + .009f * motion, scaleY = 1f + .008f * motion) {
                            drawPath(companion, companionBrush)
                            drawPath(ribbon, ribbonBrush)
                        }
                    }
                }
            }
        }
    )
}

/** The rear sheet is subdued and partly overlaps the main sheet. */
private fun buildCompanion(w: Float, h: Float): Path = Path().apply {
    moveTo(-.18f * w, .43f * h)
    cubicTo(.12f * w, .35f * h, .31f * w, .49f * h, .52f * w, .44f * h)
    cubicTo(.73f * w, .39f * h, .90f * w, .22f * h, 1.18f * w, .23f * h)
    lineTo(1.18f * w, .36f * h)
    cubicTo(.89f * w, .48f * h, .71f * w, .50f * h, .52f * w, .54f * h)
    cubicTo(.33f * w, .58f * h, .12f * w, .50f * h, -.18f * w, .55f * h)
    close()
}

/** One wide asymmetric S-shaped sheet with an open center and a broader right face. */
private fun buildRibbon(w: Float, h: Float): Path = Path().apply {
    moveTo(-.18f * w, .52f * h)
    cubicTo(.14f * w, .49f * h, .28f * w, .62f * h, .55f * w, .51f * h)
    cubicTo(.82f * w, .40f * h, .88f * w, .27f * h, 1.18f * w, .36f * h)
    lineTo(1.18f * w, .70f * h)
    cubicTo(.86f * w, .75f * h, .75f * w, .63f * h, .55f * w, .68f * h)
    cubicTo(.35f * w, .73f * h, .13f * w, .71f * h, -.18f * w, .74f * h)
    close()
}
