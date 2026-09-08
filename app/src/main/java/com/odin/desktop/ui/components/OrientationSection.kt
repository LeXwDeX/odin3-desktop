package com.odin.desktop.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.odin.desktop.R
import com.odin.desktop.service.fan.HardwareController
import com.odin.desktop.ui.components.base.OdinControl
import com.odin.desktop.ui.components.base.SettingsSectionHeader
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.theme.OdinSpacing

@Composable
internal fun OrientationSection(
    currentOrientation: Int,
    inSubMenu: Boolean,
    subFocusIndex: Int,
    onOrientationSelect: (Int) -> Unit
) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    Column {
        SettingsSectionHeader(strings.getString(R.string.text_screen_orientation),
            strings.getString(R.string.text_the_usb_port_is_on_the_bottom))
        Spacer(modifier = Modifier.height(OdinSpacing.lg))

        val options = listOf(
            Pair(strings.getString(R.string.text_fixed_landscape_default_grip), HardwareController.ORIENTATION_LANDSCAPE),
            Pair(strings.getString(R.string.text_sensor_landscape_either_landscape_direction), HardwareController.ORIENTATION_SENSOR_LANDSCAPE)
        )

        options.forEachIndexed { index, (label, mode) ->
            val isFocused = inSubMenu && subFocusIndex % options.size == index
            val isActive = currentOrientation == mode

            if (index > 0) Spacer(Modifier.height(OdinSpacing.md))
            OdinControl(
                text = label,
                onClick = { onOrientationSelect(mode) },
                selected = isActive,
                focused = isFocused,
                radio = true,
                badge = if (isActive) strings.getString(R.string.text_active) else null,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
