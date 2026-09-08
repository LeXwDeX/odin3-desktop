package com.odin.desktop.ui.components.base

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.odin.desktop.ui.theme.*

/** Shared content renderer. Pages supply values and actions, never local visual styles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OdinControl(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focused: Boolean = false,
    selected: Boolean = false,
    dangerous: Boolean = false,
    subtitle: String? = null,
    badge: String? = null,
    badgeRole: BadgeRole = BadgeRole.ACTIVE,
    radio: Boolean = false,
    toggle: Boolean = false,
    accessibilityLabel: String? = null,
    iconOnly: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    icon: (@Composable () -> Unit)? = null
) {
    val palette = LocalOdinPalette.current
    val color = when {
        !enabled -> palette.textMuted
        dangerous -> palette.danger
        focused || selected -> palette.accent
        else -> palette.text
    }
    require(!radio || !toggle) { "A control cannot be both a radio choice and a toggle" }
    val interaction = when {
        radio -> Modifier.selectable(selected, enabled, Role.RadioButton, onClick)
        toggle -> Modifier.toggleable(selected, enabled, Role.Checkbox) { onClick() }
        else -> Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    }
    OdinControlFrame(
        modifier = modifier,
        interaction = interaction.semantics { if (accessibilityLabel != null) contentDescription = accessibilityLabel },
        focused = focused, selected = selected, dangerous = dangerous, enabled = enabled, iconOnly = iconOnly
    ) {
        if (iconOnly) {
            Text(text, style = OdinTypography.body, color = color)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(OdinSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(OdinSpacing.xs)) {
                Row(Modifier.weight(1f).align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OdinSpacing.sm)) {
                    icon?.invoke()
                    Column(Modifier.weight(1f)) {
                        Text(text, style = OdinTypography.body, color = color, maxLines = maxLines,
                            overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) Text(subtitle, style = OdinTypography.caption,
                            color = if (enabled) palette.textDim else palette.textMuted)
                    }
                }
                if (badge != null) OdinBadge(badge, if (enabled) badgeRole else BadgeRole.NEUTRAL, Modifier.align(Alignment.CenterVertically))
            }
        }
    }
}
