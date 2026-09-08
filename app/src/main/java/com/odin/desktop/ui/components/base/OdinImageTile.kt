package com.odin.desktop.ui.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
            .border(if (focused) 2.dp else 1.dp, if (focused || selected) palette.accent else palette.border, shape)
            .clickable(enabled = interactive, onClick = onClick)
            .semantics {
                contentDescription = label
                if (interactive) onLongClick { onLongClick(); true }
            }
            .padding(size.inset.dp), contentAlignment = Alignment.Center, content = content)
    }
}
