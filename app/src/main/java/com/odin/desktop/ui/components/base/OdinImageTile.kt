package com.odin.desktop.ui.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.odin.desktop.ui.theme.*

enum class ImageTileSize(val slot: Int, val canvas: Int, val inset: Int) {
    STANDARD(128, 104, 16), DENSE(88, 76, 12), SWATCH(64, 56, 4)
}

/** Fixed outer slot; selection and movement only transform the inner canvas. */
@Composable
fun OdinImageTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ImageTileSize = ImageTileSize.STANDARD,
    focused: Boolean = false,
    selected: Boolean = false,
    hidden: Boolean = false,
    interactive: Boolean = true,
    transform: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalOdinPalette.current
    val shape = RoundedCornerShape(OdinCorners.card)
    Box(modifier.size(size.slot.dp).graphicsLayer { alpha = if (hidden) 0f else 1f }, contentAlignment = Alignment.Center) {
        Box(Modifier.then(transform).size(size.canvas.dp).clip(shape)
            .background(if (focused || selected) palette.selection else palette.card)
            .drawWithContent {
                drawContent()
                val stroke = if (focused) 2.dp.toPx() else 1.dp.toPx()
                val inset = stroke / 2f
                drawRoundRect(
                    color = if (focused) palette.focus else if (selected) palette.accent else palette.border.copy(alpha = 0.28f),
                    topLeft = Offset(inset, inset),
                    size = Size(this.size.width - stroke, this.size.height - stroke),
                    cornerRadius = CornerRadius(OdinCorners.card.toPx()),
                    style = Stroke(stroke)
                )
                if (focused) {
                    val innerInset = 4.dp.toPx()
                    drawRoundRect(
                        color = palette.accent.copy(alpha = 0.6f),
                        topLeft = Offset(innerInset, innerInset),
                        size = Size(this.size.width - 2 * innerInset, this.size.height - 2 * innerInset),
                        cornerRadius = CornerRadius((OdinCorners.card - 4.dp).toPx()),
                        style = Stroke(1.dp.toPx())
                    )
                }
            }
            .clickable(enabled = interactive, onClick = onClick)
            .semantics {
                contentDescription = label
                if (interactive) onLongClick { onLongClick(); true }
            }
            .padding(size.inset.dp), contentAlignment = Alignment.Center, content = content)
    }
}
