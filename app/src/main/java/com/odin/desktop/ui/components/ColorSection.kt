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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ColorSection(
    currentColor: String,
    inSubMenu: Boolean,
    subFocusIndex: Int,
    onColorSelect: (String) -> Unit
) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    val presets = listOf(
        Pair(strings.getString(R.string.text_cyan_default), "#ff00e5ff"),
        Pair(strings.getString(R.string.text_purple), "#ff7c4dff"),
        Pair(strings.getString(R.string.text_red), "#ffff5252"),
        Pair(strings.getString(R.string.text_green), "#ff00e676"),
        Pair(strings.getString(R.string.text_white), "#ffffffff"),
        Pair(strings.getString(R.string.text_dark_gray), "#ff2e2e2e")
    )

    Column {
        Text(strings.getString(R.string.text_choose_a_stick_led_color_a_to), color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            presets.forEachIndexed { index, (label, hex) ->
                val isSelected = currentColor.equals(hex, ignoreCase = true)
                val isFocused = inSubMenu && subFocusIndex % presets.size == index

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onColorSelect(hex) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(hex)))
                            .border(
                                width = if (isFocused) 3.5.dp else if (isSelected) 2.dp else 1.dp,
                                color = if (isFocused) palette.accent else if (isSelected) palette.text else palette.border,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = label,
                        color = if (isFocused) palette.accent else if (isSelected) palette.text else palette.textDim,
                        fontSize = 12.sp,
                        fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
