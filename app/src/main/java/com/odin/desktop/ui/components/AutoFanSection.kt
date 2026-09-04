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

@Composable
internal fun AutoFanSection(
    autoFanControlEnabled: Boolean,
    socTemp: Float,
    inSubMenu: Boolean,
    subFocusIndex: Int,
    onToggleAutoFan: () -> Unit
) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    Column {
        Text(strings.getString(R.string.text_automatic_fan_policy), color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(strings.getString(R.string.text_the_fan_can_stop_while_charging_outside), color = palette.textDim, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(18.dp))

        val isFocused = inSubMenu && subFocusIndex == 0

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isFocused) palette.accent.copy(alpha = 0.20f)
                    else if (autoFanControlEnabled) palette.surface
                    else palette.card
                )
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) palette.accent else palette.border,
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable { onToggleAutoFan() }
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = strings.getString(R.string.text_automatic_fan_control),
                        color = if (isFocused) palette.accent else palette.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (autoFanControlEnabled) strings.getString(R.string.text_automatic_control_enabled_a_or_tap_to) else strings.getString(R.string.text_automatic_control_disabled_manual_mode_retained),
                        color = palette.textDim,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = if (autoFanControlEnabled) strings.getString(R.string.text_on_3) else strings.getString(R.string.text_off_3),
                    color = if (autoFanControlEnabled) palette.active else palette.textDim,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 实时状态监控与保护机制
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(palette.card)
                .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(strings.getString(R.string.text_temperature_and_cooling_status), color = palette.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(strings.getString(R.string.text_highest_chip_temperature_soc), color = palette.text, fontSize = 13.sp)
                Text(
                    text = if (socTemp.isFinite()) "${"%.1f".format(socTemp)} °C" else "— °C",
                    color = if (!socTemp.isFinite()) palette.textDim else if (socTemp <= 60f) palette.active else palette.warning,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(strings.getString(R.string.text_cooling_threshold), color = palette.text, fontSize = 13.sp)
                Text(strings.getString(R.string.text_60_0_c_cooling_required_above_this), color = palette.warning, fontSize = 13.sp)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(strings.getString(R.string.text_automatic_stop_conditions), color = palette.text, fontSize = 13.sp)
                Text(strings.getString(R.string.text_charging_no_game_temperature_60_c), color = palette.textDim, fontSize = 12.sp)
            }
        }
    }
}
