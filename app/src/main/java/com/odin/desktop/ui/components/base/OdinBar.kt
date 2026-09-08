package com.odin.desktop.ui.components.base

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.odin.desktop.ui.theme.*

/** Null data is striped; it never renders as a measured zero. */
@Composable
fun OdinBar(fractions: List<Float>?, colors: List<Color>, modifier: Modifier = Modifier) {
    val palette = LocalOdinPalette.current
    Canvas(modifier.fillMaxWidth().height(OdinSizes.barHeight).clip(RoundedCornerShape(OdinCorners.badge))) {
        drawRect(palette.track)
        if (fractions == null) {
            var x = -size.height
            while (x < size.width) {
                drawLine(palette.border, Offset(x, size.height), Offset(x + size.height, 0f), 2.dp.toPx())
                x += 12.dp.toPx()
            }
        } else {
            var offset = 0f
            fractions.forEachIndexed { index, fraction ->
                val normalized = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
                val width = (normalized * size.width).coerceAtMost(size.width - offset)
                if (width > 0f) drawRect(colors.getOrElse(index) { palette.track }, Offset(offset, 0f), Size(width, size.height))
                offset += width
            }
        }
    }
}
