package com.odin.desktop.ui.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.theme.OdinCorners
import com.odin.desktop.ui.theme.OdinInsets
import com.odin.desktop.ui.theme.OdinTypography

enum class BadgeRole { ACTIVE, INFO, WARNING, NEUTRAL, DANGER }

/** One visual family for state and category labels; role selects meaning, not geometry. */
@Composable
fun OdinBadge(text: String, role: BadgeRole = BadgeRole.INFO, modifier: Modifier = Modifier) {
    val palette = LocalOdinPalette.current
    val color = when (role) {
        BadgeRole.ACTIVE -> palette.active
        BadgeRole.INFO -> palette.accent
        BadgeRole.WARNING -> palette.warning
        BadgeRole.NEUTRAL -> palette.textDim
        BadgeRole.DANGER -> palette.danger
    }
    val shape = RoundedCornerShape(OdinCorners.badge)
    Box(modifier.background(palette.surface, shape)
        .background(color.copy(alpha = 0.12f), shape)
        .border(1.dp, color.copy(alpha = 0.65f), shape)
        .padding(OdinInsets.badge)) {
        // A shared line slot absorbs fallback-glyph metrics (for example ✓ and CJK).
        Text(text, color = color, style = OdinTypography.caption,
            modifier = Modifier.heightIn(min = with(LocalDensity.current) { OdinTypography.body.lineHeight.toDp() })
                .wrapContentHeight())
    }
}
