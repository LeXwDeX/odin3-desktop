package com.odin.desktop.ui.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.odin.desktop.ui.theme.*

@Composable
fun OdinEqualHeightRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(OdinSpacing.sm),
        verticalAlignment = Alignment.CenterVertically, content = content)
}

enum class SurfaceRole { PANEL, CARD, DENSE, NAVIGATION, MODAL }

@Composable
fun OdinSurface(modifier: Modifier = Modifier, role: SurfaceRole = SurfaceRole.CARD,
    focused: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalOdinPalette.current
    val shape = RoundedCornerShape(if (role == SurfaceRole.MODAL) OdinCorners.dialog else OdinCorners.card)
    val inset = when (role) {
        SurfaceRole.PANEL, SurfaceRole.MODAL -> OdinSpacing.panel
        SurfaceRole.CARD -> OdinSpacing.card
        SurfaceRole.DENSE -> OdinSpacing.denseCard
        SurfaceRole.NAVIGATION -> OdinSpacing.sm
    }
    Column(modifier.clip(shape).background(if (focused) palette.selection else palette.surface)
        .border(if (focused) 2.dp else 1.dp, if (focused) palette.accent else palette.border, shape)
        .padding(inset), content = content)
}
