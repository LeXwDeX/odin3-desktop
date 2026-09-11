package com.odin.desktop.ui.components.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun OdinActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focused: Boolean = false,
    dangerous: Boolean = false,
    accessibilityLabel: String? = null,
    iconOnly: Boolean = false,
    emoji: String? = null
) = OdinControl(text = text, onClick = onClick, modifier = modifier, enabled = enabled,
    focused = focused, dangerous = dangerous, accessibilityLabel = accessibilityLabel, iconOnly = iconOnly,
    icon = emoji?.let { { OdinEmojiIcon(it) } })
