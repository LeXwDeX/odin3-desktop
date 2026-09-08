package com.odin.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.odin.desktop.R
import com.odin.desktop.ui.components.base.ImageTileSize
import com.odin.desktop.ui.components.base.OdinImageTile
import com.odin.desktop.ui.components.base.SettingsSectionHeader
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.theme.OdinSpacing
import com.odin.desktop.ui.theme.OdinTypography

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
        SettingsSectionHeader(strings.getString(R.string.text_choose_a_stick_led_color_a_to))
        Spacer(modifier = Modifier.height(OdinSpacing.lg))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OdinSpacing.md)) {
            presets.forEachIndexed { index, (label, hex) ->
                val isSelected = currentColor.equals(hex, ignoreCase = true)
                val isFocused = inSubMenu && subFocusIndex % presets.size == index

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    OdinImageTile(label = label, onClick = { onColorSelect(hex) }, size = ImageTileSize.SWATCH,
                        focused = isFocused, selected = isSelected) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(hex))))
                    }
                    Spacer(modifier = Modifier.height(OdinSpacing.sm))
                    Text(
                        text = label,
                        textAlign = TextAlign.Center,
                        color = if (isFocused) palette.accent else if (isSelected) palette.text else palette.textDim,
                        style = OdinTypography.caption
                    )
                }
            }
        }
    }
}
