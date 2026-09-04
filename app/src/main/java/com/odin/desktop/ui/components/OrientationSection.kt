package com.odin.desktop.ui.components

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
import androidx.compose.ui.unit.sp
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
        Text(strings.getString(R.string.text_screen_orientation), color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(strings.getString(R.string.text_the_usb_port_is_on_the_bottom), color = palette.textDim, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(16.dp))

        val options = listOf(
            Pair(strings.getString(R.string.text_fixed_landscape_default_grip), HardwareController.ORIENTATION_LANDSCAPE),
            Pair(strings.getString(R.string.text_sensor_landscape_either_landscape_direction), HardwareController.ORIENTATION_SENSOR_LANDSCAPE)
        )

        options.forEachIndexed { index, (label, mode) ->
            val isFocused = inSubMenu && subFocusIndex % options.size == index
            val isActive = currentOrientation == mode

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isFocused) palette.accent.copy(alpha = 0.20f)
                        else if (isActive) palette.surface
                        else palette.card
                    )
                    .border(
                        width = if (isFocused) 2.dp else if (isActive) 1.dp else 0.dp,
                        color = if (isFocused) palette.accent else if (isActive) palette.accent.copy(alpha = 0.5f) else palette.border,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onOrientationSelect(mode) }
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = if (isFocused || isActive) palette.accent else palette.text,
                        fontSize = 14.sp,
                        fontWeight = if (isFocused || isActive) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isActive) {
                        Text(
                            text = strings.getString(R.string.text_active),
                            color = palette.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
