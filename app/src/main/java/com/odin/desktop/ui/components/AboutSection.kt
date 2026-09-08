package com.odin.desktop.ui.components

import com.odin.desktop.ui.components.base.SettingsSectionHeader
import com.odin.desktop.ui.theme.OdinSpacing
import com.odin.desktop.ui.theme.OdinTypography
import com.odin.desktop.ui.theme.LocalOdinPalette
import androidx.compose.ui.platform.LocalContext
import com.odin.desktop.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun AboutSection() {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    Column {
        SettingsSectionHeader(strings.getString(R.string.text_odin_3_handheld_launcher),
            "Odin Desktop ${com.odin.desktop.BuildConfig.VERSION_NAME}")
        Spacer(modifier = Modifier.height(OdinSpacing.lg))

        Text(
            text = strings.getString(R.string.text_a_desktop_and_system_controls_for_ayn) +
                    strings.getString(R.string.text_oled_black_background_and_burn_in_protection) +
                    strings.getString(R.string.text_full_gamepad_navigation_n) +
                    strings.getString(R.string.text_default_home_and_startup_integration_n) +
                    strings.getString(R.string.text_manual_hardware_control) +
                    strings.getString(R.string.text_open_source_acknowledgements_n) +
                    "- Android Jetpack & Compose\n" +
                    "- Room Persistence Library\n" +
                    "- Kotlin Coroutines\n",
            color = palette.text,
            style = OdinTypography.body
        )
    }
}
