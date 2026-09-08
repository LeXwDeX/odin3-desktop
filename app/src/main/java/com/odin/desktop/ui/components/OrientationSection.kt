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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.odin.desktop.service.fan.HardwareController

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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(OdinCorners.control))
                    .background(
                        if (isFocused) palette.selection
                        else if (isActive) palette.surface
                        else palette.card
                    )
                    .border(
                        width = if (isFocused) 2.dp else if (isActive) 1.dp else 0.dp,
                        color = if (isFocused) palette.accent else if (isActive) palette.accent.copy(alpha = 0.5f) else palette.border,
                        shape = RoundedCornerShape(OdinCorners.control)
                    )
                    .clickable { onOrientationSelect(mode) }
                    .padding(OdinInsets.optionRow)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f).padding(end = OdinSpacing.sm),
                        color = if (isFocused || isActive) palette.accent else palette.text,
                        style = OdinTypography.h3,
                        fontWeight = if (isFocused || isActive) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isActive) {
                        Text(
                            text = strings.getString(R.string.text_active),
                            color = palette.accent,
                            style = OdinTypography.button,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
