package com.odin.desktop.ui.components.base

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.theme.OdinSizes

/** Decorative, single-color symbols. The adjacent control supplies the accessible name. */
enum class OdinSymbol {
    DASHBOARD, FILE, SETTINGS, ODIN_SETTINGS, MANAGE, SORT,
    PERFORMANCE, FAN, LIGHTS, CHARGING, AIRPLANE
}

@Composable
fun OdinSymbolIcon(symbol: OdinSymbol, modifier: Modifier = Modifier, tint: Color? = null) {
    val color = tint ?: LocalOdinPalette.current.textDim
    Canvas(modifier.size(OdinSizes.icon).clearAndSetSemantics {}) {
        val unit = size.minDimension / 24f
        val stroke = 1.8f * unit
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, point(x1, y1), point(x2, y2), stroke, cap = StrokeCap.Round)
        fun circle(x: Float, y: Float, radius: Float) =
            drawCircle(color, radius * unit, point(x, y), style = Stroke(stroke))
        fun roundRect(x: Float, y: Float, w: Float, h: Float, radius: Float = 2f) =
            drawRoundRect(color, point(x, y), Size(w * unit, h * unit),
                CornerRadius(radius * unit), style = Stroke(stroke))
        fun path(vararg coordinates: Float) {
            val p = Path().apply {
                moveTo(coordinates[0] * unit, coordinates[1] * unit)
                var index = 2
                while (index < coordinates.size) {
                    lineTo(coordinates[index] * unit, coordinates[index + 1] * unit)
                    index += 2
                }
            }
            drawPath(p, color, style = Stroke(stroke, cap = StrokeCap.Round))
        }

        when (symbol) {
            OdinSymbol.DASHBOARD -> {
                roundRect(3f, 3f, 8f, 8f)
                roundRect(13f, 3f, 8f, 8f)
                roundRect(3f, 13f, 8f, 8f)
                roundRect(13f, 13f, 8f, 8f)
            }
            OdinSymbol.FILE -> {
                path(3f, 7f, 9f, 7f, 11f, 9f, 21f, 9f, 21f, 19f, 3f, 19f, 3f, 7f)
                line(3f, 10f, 21f, 10f)
            }
            OdinSymbol.SETTINGS, OdinSymbol.ODIN_SETTINGS -> {
                circle(12f, 12f, 3.2f)
                for (index in 0 until 8) {
                    val angle = Math.PI * index / 4.0
                    val dx = kotlin.math.cos(angle).toFloat()
                    val dy = kotlin.math.sin(angle).toFloat()
                    line(12f + dx * 7f, 12f + dy * 7f, 12f + dx * 9f, 12f + dy * 9f)
                }
                if (symbol == OdinSymbol.ODIN_SETTINGS) circle(12f, 12f, 6.6f)
            }
            OdinSymbol.MANAGE -> {
                line(4f, 6f, 20f, 6f)
                line(4f, 12f, 20f, 12f)
                line(4f, 18f, 20f, 18f)
                circle(9f, 6f, 1.8f)
                circle(16f, 12f, 1.8f)
                circle(11f, 18f, 1.8f)
            }
            OdinSymbol.SORT -> {
                line(5f, 6f, 18f, 6f)
                line(5f, 11f, 15f, 11f)
                line(5f, 16f, 12f, 16f)
                path(17f, 15f, 17f, 20f, 20f, 17f)
            }
            OdinSymbol.PERFORMANCE -> {
                drawArc(color, 180f, 180f, false, point(3f, 3f), Size(18f * unit, 18f * unit),
                    style = Stroke(stroke, cap = StrokeCap.Round))
                line(5f, 18f, 19f, 18f)
                line(12f, 15f, 17f, 10f)
                circle(12f, 15f, 1f)
            }
            OdinSymbol.FAN -> {
                circle(12f, 12f, 8.5f)
                circle(12f, 12f, 1.5f)
                path(12f, 9f, 10f, 5f, 12f, 4f, 14f, 5f, 12f, 9f)
                path(14.5f, 13f, 19f, 11f, 20f, 13f, 19f, 15f, 14.5f, 13f)
                path(10f, 14f, 10f, 19f, 8f, 20f, 6f, 18f, 10f, 14f)
            }
            OdinSymbol.LIGHTS -> {
                drawArc(color, 185f, 170f, false, point(6f, 3f), Size(12f * unit, 13f * unit),
                    style = Stroke(stroke, cap = StrokeCap.Round))
                path(8f, 13f, 9f, 17f, 15f, 17f, 16f, 13f)
                line(9f, 20f, 15f, 20f)
                line(12f, 1f, 12f, 2f)
                line(3f, 9f, 4f, 9f)
                line(20f, 9f, 21f, 9f)
            }
            OdinSymbol.CHARGING -> {
                roundRect(3f, 6f, 17f, 12f)
                line(21f, 10f, 21f, 14f)
                path(12f, 8f, 9f, 13f, 12f, 13f, 11f, 16f, 15f, 11f, 12f, 11f, 12f, 8f)
            }
            OdinSymbol.AIRPLANE -> {
                path(3f, 13f, 10f, 11f, 11f, 4f, 13f, 2f, 14f, 11f, 21f, 13f,
                    21f, 15f, 14f, 15f, 14f, 19f, 17f, 21f, 17f, 22f,
                    12f, 20f, 7f, 22f, 7f, 21f, 10f, 19f, 10f, 15f, 3f, 15f, 3f, 13f)
            }
        }
    }
}
