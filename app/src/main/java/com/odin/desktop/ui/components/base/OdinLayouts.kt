package com.odin.desktop.ui.components.base

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val radius = if (role == SurfaceRole.MODAL) OdinCorners.dialog else OdinCorners.card
    val shape = RoundedCornerShape(radius)
    val inset = when (role) {
        SurfaceRole.PANEL, SurfaceRole.MODAL -> OdinSpacing.panel
        SurfaceRole.CARD -> OdinSpacing.card
        SurfaceRole.DENSE -> OdinSpacing.denseCard
        SurfaceRole.NAVIGATION -> OdinSpacing.sm
    }
    val glassRole = when (role) {
        SurfaceRole.PANEL -> GlassRole.PANEL
        SurfaceRole.CARD -> GlassRole.CARD
        SurfaceRole.DENSE -> GlassRole.DENSE
        SurfaceRole.NAVIGATION -> GlassRole.NAVIGATION
        SurfaceRole.MODAL -> GlassRole.MODAL
    }
    Column(modifier.clip(shape)
        .odinGlassSurface(palette, glassRole, radius, emphasized = focused)
        .odinFocusHalo(focused = focused, selected = false, enabled = true,
            radius = radius, focusColor = palette.focus, selectionColor = palette.focus)
        .padding(inset), content = content)
}
