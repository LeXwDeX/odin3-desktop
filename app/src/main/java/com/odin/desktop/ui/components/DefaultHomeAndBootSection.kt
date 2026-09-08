package com.odin.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.odin.desktop.R
import com.odin.desktop.ui.components.base.*
import com.odin.desktop.ui.theme.*

@Composable
internal fun DefaultHomeAndBootSection(isDefaultHome: Boolean, inSubMenu: Boolean, onRequestDefaultHome: () -> Unit) {
    val strings = LocalContext.current
    val palette = LocalOdinPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(OdinSpacing.lg)) {
        SettingsSectionHeader(strings.getString(R.string.text_default_home_and_startup),
            strings.getString(R.string.text_use_odin_desktop_as_the_system_home))
        OdinSurface(Modifier.fillMaxWidth()) {
            Text(strings.getString(R.string.text_default_home_screen), style = OdinTypography.body, color = palette.text)
            Spacer(Modifier.height(OdinSpacing.sm))
            OdinBadge(strings.getString(if (isDefaultHome) R.string.text_set_as_default else R.string.text_not_the_default),
                if (isDefaultHome) BadgeRole.ACTIVE else BadgeRole.WARNING)
            Spacer(Modifier.height(OdinSpacing.sm))
            Text(strings.getString(if (isDefaultHome) R.string.text_the_home_button_opens_odin_desktop else R.string.text_press_a_or_tap_to_choose_odin),
                style = OdinTypography.caption, color = palette.textDim)
            Spacer(Modifier.height(OdinSpacing.lg))
            OdinActionButton(strings.getString(if (isDefaultHome) R.string.text_manage_home_settings_a else R.string.text_set_as_default_a),
                onClick = onRequestDefaultHome, focused = inSubMenu, modifier = Modifier.fillMaxWidth())
        }
    }
}
