package com.odin.desktop.ui.components.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.odin.desktop.ui.theme.*

/** One measured shell for actions, choices and text fields. */
@Composable
internal fun OdinControlFrame(
    modifier: Modifier = Modifier,
    interaction: Modifier = Modifier,
    focused: Boolean = false,
    selected: Boolean = false,
    dangerous: Boolean = false,
    enabled: Boolean = true,
    iconOnly: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalOdinPalette.current
    val hasFocus = enabled && focused
    val shape = RoundedCornerShape(OdinCorners.control)
    Box(
        modifier.heightIn(min = OdinSizes.scaledControlHeight())
            .clip(shape)
            .odinGlassSurface(
                palette = palette, role = GlassRole.CONTROL,
                radius = OdinCorners.control, emphasized = enabled && (hasFocus || selected)
            )
            .odinFocusHalo(
                focused = hasFocus, selected = selected, enabled = enabled,
                radius = OdinCorners.control,
                focusColor = if (dangerous) palette.danger else palette.focus,
                selectionColor = if (dangerous) palette.danger else palette.focus
            )
            .then(interaction)
            .padding(if (iconOnly) OdinInsets.iconControl else OdinInsets.control),
        contentAlignment = if (iconOnly) Alignment.Center else Alignment.CenterStart,
        content = content
    )
}
