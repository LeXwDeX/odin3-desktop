package com.odin.desktop.ui.components

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
import androidx.compose.ui.unit.sp

@Composable
internal fun AboutSection() {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    Column {
        Text(strings.getString(R.string.text_odin_3_handheld_launcher), color = palette.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Odin Desktop ${com.odin.desktop.BuildConfig.VERSION_NAME}", color = palette.textDim, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = strings.getString(R.string.text_a_desktop_and_system_controls_for_ayn) +
                    strings.getString(R.string.text_oled_black_background_and_burn_in_protection) +
                    strings.getString(R.string.text_full_gamepad_navigation_n) +
                    strings.getString(R.string.text_default_home_and_startup_integration_n) +
                    strings.getString(R.string.text_charging_fan_control_n_n) +
                    strings.getString(R.string.text_open_source_acknowledgements_n) +
                    "- Android Jetpack & Compose\n" +
                    "- Room Persistence Library\n" +
                    "- Kotlin Coroutines\n",
            color = palette.text,
            fontSize = 13.sp,
            lineHeight = 22.sp
        )
    }
}
