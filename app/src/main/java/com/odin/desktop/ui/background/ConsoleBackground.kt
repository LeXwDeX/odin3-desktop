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
            val foldedFaceBrush = Brush.verticalGradient(
                colors = listOf(Color(0x003E7AC2), Color(0x3D65A5E7), Color.Transparent),
                startY = height * .33f,
                endY = height * .61f
            )
            // Reuse the paths while their control points slowly change at each draw.
            val companion = Path()
            val ribbon = Path()
            val foldedFace = Path()

            onDrawBehind {
                drawRect(background)
                drawRect(softLight)
                if (width <= 0f || height <= 0f) return@onDrawBehind

                // Snapshot state is read only here. This invalidates the draw node, not the UI tree.
                val sway = easedWave(phase.floatValue)
                val fold = easedWave(phase.floatValue + .04f)
                buildCompanion(companion, width, height, sway)
                buildRibbon(ribbon, foldedFace, width, height, sway, fold)
                clipRect {
                    drawPath(companion, companionBrush)
                    drawPath(ribbon, ribbonBrush)
                    drawPath(foldedFace, foldedFaceBrush)
                }
            }
        }
    )
}

/** A faint continuation behind the main fold, sharing its motion. */
private fun buildCompanion(path: Path, w: Float, h: Float, sway: Float) {
    path.reset()
    path.moveTo(-.14f * w, (.30f + .012f * sway) * h)
    path.cubicTo(.17f * w, (.36f + .018f * sway) * h, .47f * w, (.43f - .012f * sway) * h,
        1.14f * w, (.29f - .015f * sway) * h)
    path.lineTo(1.14f * w, (.39f - .010f * sway) * h)
    path.cubicTo(.56f * w, (.57f - .012f * sway) * h, .16f * w, (.46f + .014f * sway) * h,
        -.14f * w, (.41f + .012f * sway) * h)
    path.close()
}

/** A single broad sheet pinches at the middle and opens into a second face. */
private fun buildRibbon(body: Path, face: Path, w: Float, h: Float, sway: Float, fold: Float) {
    val waistX = (.49f + .018f * sway) * w
    val waistTop = (.485f + .023f * sway) * h
    val waistBottom = (.53f + .012f * sway + .012f * fold) * h
    val leftTop = (.38f + .014f * sway) * h
    val rightTop = (.40f - .017f * sway) * h

    body.reset()
    traceRibbonTop(body, w, h, waistX, waistTop, leftTop, rightTop, sway, fold)
    body.lineTo(1.14f * w, (.67f - .010f * sway + .015f * fold) * h)
    body.cubicTo(.88f * w, (.75f - .019f * sway) * h,
        .69f * w, (.57f + .019f * fold) * h, waistX, waistBottom)
    body.cubicTo(.33f * w, (.56f + .014f * fold) * h,
        .13f * w, (.69f + .016f * sway) * h,
        -.14f * w, (.64f + .012f * sway) * h)
    body.close()

    face.reset()
    traceRibbonTop(face, w, h, waistX, waistTop, leftTop, rightTop, sway, fold)
    face.lineTo(1.14f * w, (.55f - .012f * sway) * h)
    face.cubicTo(.85f * w, (.57f - .011f * sway) * h,
        .66f * w, (.525f + .015f * fold) * h,
        waistX, (waistTop + waistBottom) * .5f)
    face.cubicTo(.32f * w, (.535f + .012f * fold) * h,
        .13f * w, (.48f + .014f * sway) * h,
        -.14f * w, (.49f + .012f * sway) * h)
    face.close()
}

private fun traceRibbonTop(
    path: Path, w: Float, h: Float, waistX: Float, waistTop: Float,
    leftTop: Float, rightTop: Float, sway: Float, fold: Float
) {
    path.moveTo(-.14f * w, leftTop)
    path.cubicTo(.15f * w, (.31f + .021f * sway) * h,
        .35f * w, (.50f + .012f * fold) * h, waistX, waistTop)
    path.cubicTo(.68f * w, (.46f - .014f * fold) * h,
        .85f * w, (.31f - .022f * sway) * h,
        1.14f * w, rightTop)
}
