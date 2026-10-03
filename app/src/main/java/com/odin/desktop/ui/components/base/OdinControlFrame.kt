package com.odin.desktop.ui.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.odin.desktop.ui.theme.*

enum class OdinControlAppearance { STANDARD, DOCK }

val LocalOdinControlAppearance = staticCompositionLocalOf { OdinControlAppearance.STANDARD }

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
    val isDock = LocalOdinControlAppearance.current == OdinControlAppearance.DOCK
    val hasFocus = enabled && focused
    val shape = RoundedCornerShape(OdinCorners.control)
    Box(
        modifier.heightIn(min = OdinSizes.scaledControlHeight())
            .clip(shape)
            .background(if (enabled && (hasFocus || selected)) palette.selection
                else if (isDock) palette.surface.copy(alpha = 0.58f) else palette.card)
            .border(if (hasFocus) 2.dp else 1.dp,
                if (hasFocus) (if (dangerous) palette.danger else palette.focus)
                else if (isDock) androidx.compose.ui.graphics.Color.Transparent
                else palette.border.copy(alpha = if (enabled) 0.22f else 0.12f), shape)
            .then(interaction)
            .padding(if (iconOnly) OdinInsets.iconControl else OdinInsets.control),
        contentAlignment = if (iconOnly) Alignment.Center else Alignment.CenterStart,
        content = content
    )
}
