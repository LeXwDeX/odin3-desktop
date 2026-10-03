package com.odin.desktop.ui.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.theme.OdinCorners
import com.odin.desktop.ui.theme.OdinSizes
import com.odin.desktop.ui.theme.OdinSpacing
import com.odin.desktop.ui.theme.OdinTypography

/** Text navigation with a persistent selection mark and a separate controller focus ring. */
@Composable
fun OdinNavigationTab(
    label: String,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null
) {
    val palette = LocalOdinPalette.current
    val shape = RoundedCornerShape(OdinCorners.control)
    Box(
        modifier.widthIn(max = 150.dp).width(IntrinsicSize.Max)
            .heightIn(min = OdinSizes.scaledControlHeight())
            .clip(shape)
            .background(if (focused) palette.selection.copy(alpha = 0.7f) else androidx.compose.ui.graphics.Color.Transparent)
            .odinFocusHalo(
                focused = focused, selected = false, enabled = true,
                radius = OdinCorners.control, focusColor = palette.focus,
                selectionColor = palette.accent
            )
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.padding(horizontal = OdinSpacing.md, vertical = OdinSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OdinSpacing.sm)
        ) {
            icon?.invoke()
            Text(label, color = if (selected || focused) palette.text else palette.textDim,
                style = OdinTypography.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) {
            Box(Modifier.align(Alignment.BottomCenter).width(22.dp).height(2.dp)
                .background(palette.accent, RoundedCornerShape(1.dp)))
        }
    }
}
