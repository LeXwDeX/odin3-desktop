package com.odin.desktop.ui.components.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.theme.OdinSizes
import com.odin.desktop.ui.theme.OdinTypography

/** Decorative control icon. The parent OdinControl owns the icon-to-label spacing. */
@Composable
fun OdinEmojiIcon(emoji: String) {
    Box(Modifier.size(OdinSizes.icon).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Text(emoji, style = OdinTypography.body, color = LocalOdinPalette.current.text)
    }
}
