package com.odin.desktop.ui.components.base

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A quiet, feathered vector outline shared by controls, image tiles and navigation. */
internal fun Modifier.odinFocusHalo(
    focused: Boolean,
    selected: Boolean,
    enabled: Boolean,
    radius: Dp,
    focusColor: Color,
    selectionColor: Color,
    idleColor: Color = Color.Transparent
): Modifier = drawWithCache {
    val focusVisible = focused && enabled
    val soft = Stroke(4.dp.toPx())
    val middle = Stroke(2.5.dp.toPx())
    val core = Stroke(1.2.dp.toPx())
    val idle = Stroke(1.dp.toPx())
    val corner = radius.toPx()

    fun androidx.compose.ui.graphics.drawscope.DrawScope.outline(color: Color, stroke: Stroke) {
        val inset = stroke.width / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(size.width - 2f * inset, size.height - 2f * inset),
            cornerRadius = CornerRadius((corner - inset).coerceAtLeast(0f)),
            style = stroke
        )
    }

    onDrawWithContent {
        drawContent()
        if (focusVisible) {
            outline(focusColor.copy(alpha = 0.08f), soft)
            outline(focusColor.copy(alpha = 0.18f), middle)
            outline(focusColor.copy(alpha = 0.64f), core)
        } else {
            val outlineColor = if (enabled && selected) selectionColor.copy(alpha = 0.48f) else idleColor
            if (outlineColor.alpha > 0f) outline(outlineColor, idle)
        }
    }
}
