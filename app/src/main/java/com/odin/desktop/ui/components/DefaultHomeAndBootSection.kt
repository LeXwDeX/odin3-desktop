package com.odin.desktop.ui.components

import com.odin.desktop.ui.components.base.SettingsSectionHeader
import com.odin.desktop.ui.theme.OdinCorners
import com.odin.desktop.ui.theme.OdinInsets
import com.odin.desktop.ui.theme.OdinSpacing
import com.odin.desktop.ui.theme.OdinTypography
import com.odin.desktop.ui.theme.LocalOdinPalette
import androidx.compose.ui.platform.LocalContext
import com.odin.desktop.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun DefaultHomeAndBootSection(
    isDefaultHome: Boolean,
    inSubMenu: Boolean,
    onRequestDefaultHome: () -> Unit
) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    Column {
        SettingsSectionHeader(strings.getString(R.string.text_default_home_and_startup),
            strings.getString(R.string.text_use_odin_desktop_as_the_system_home))
        Spacer(modifier = Modifier.height(OdinSpacing.lg))

        // 设为系统默认桌面
        val isHomeFocused = inSubMenu
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(OdinCorners.card))
                .background(if (isHomeFocused) palette.selection else palette.card)
                .border(
                    width = if (isHomeFocused) 2.dp else 1.dp,
                    color = if (isHomeFocused) palette.accent else palette.border,
                    shape = RoundedCornerShape(OdinCorners.card)
                )
                .clickable { onRequestDefaultHome() }
                .padding(OdinSpacing.card)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(OdinSpacing.md)
            ) {
                Text(
                    text = strings.getString(R.string.text_default_home_screen),
                    color = if (isHomeFocused) palette.accent else palette.text,
                    style = OdinTypography.h3,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(OdinCorners.badge))
                        .background(if (isDefaultHome) palette.active.copy(alpha = 0.15f) else palette.warning.copy(alpha = 0.15f))
                        .border(
                            width = 1.dp,
                            color = if (isDefaultHome) palette.active else palette.warning,
                            shape = RoundedCornerShape(OdinCorners.badge)
                        )
                        .padding(OdinInsets.badge)
                ) {
                    Text(
                        text = if (isDefaultHome) strings.getString(R.string.text_set_as_default) else strings.getString(R.string.text_not_the_default),
                        color = if (isDefaultHome) palette.active else palette.warning,
                        style = OdinTypography.caption,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (isDefaultHome) strings.getString(R.string.text_the_home_button_opens_odin_desktop)
                           else strings.getString(R.string.text_press_a_or_tap_to_choose_odin),
                    color = palette.textDim,
                    style = OdinTypography.body
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Start)
                        .clip(RoundedCornerShape(OdinCorners.control))
                        .background(if (isHomeFocused) palette.accent else palette.surface)
                        .border(1.dp, if (isHomeFocused) palette.accent else palette.border, RoundedCornerShape(OdinCorners.control))
                        .padding(OdinInsets.button)
                ) {
                    Text(
                        text = if (isDefaultHome) strings.getString(R.string.text_manage_home_settings_a) else strings.getString(R.string.text_set_as_default_a),
                        color = if (isHomeFocused) palette.background else (if (isDefaultHome) palette.text else palette.accent),
                        style = OdinTypography.button
                    )
                }
            }
        }
    }
}
