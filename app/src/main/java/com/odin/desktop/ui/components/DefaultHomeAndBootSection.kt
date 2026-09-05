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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DefaultHomeAndBootSection(
    isDefaultHome: Boolean,
    inSubMenu: Boolean,
    onRequestDefaultHome: () -> Unit
) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    Column {
        Text(strings.getString(R.string.text_default_home_and_startup), color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(strings.getString(R.string.text_use_odin_desktop_as_the_system_home), color = palette.textDim, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(16.dp))

        // 设为系统默认桌面
        val isHomeFocused = inSubMenu
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isHomeFocused) palette.accent.copy(alpha = 0.20f) else palette.card)
                .border(
                    width = if (isHomeFocused) 2.dp else 1.dp,
                    color = if (isHomeFocused) palette.accent else palette.border,
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable { onRequestDefaultHome() }
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = strings.getString(R.string.text_default_home_screen),
                            color = if (isHomeFocused) palette.accent else palette.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isDefaultHome) palette.active.copy(alpha = 0.15f) else palette.warning.copy(alpha = 0.15f))
                                .border(
                                    width = 1.dp,
                                    color = if (isDefaultHome) palette.active else palette.warning,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isDefaultHome) strings.getString(R.string.text_set_as_default) else strings.getString(R.string.text_not_the_default),
                                color = if (isDefaultHome) palette.active else palette.warning,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isDefaultHome) strings.getString(R.string.text_the_home_button_opens_odin_desktop)
                               else strings.getString(R.string.text_press_a_or_tap_to_choose_odin),
                        color = palette.textDim,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isHomeFocused) palette.accent else palette.surface)
                        .border(1.dp, if (isHomeFocused) palette.accent else palette.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isDefaultHome) strings.getString(R.string.text_manage_home_settings_a) else strings.getString(R.string.text_set_as_default_a),
                        color = if (isHomeFocused) palette.background else (if (isDefaultHome) palette.text else palette.accent),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
