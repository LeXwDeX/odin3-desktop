package com.odin.desktop.ui.components.base

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.theme.OdinTypography

@Composable
fun OdinTextField(value: String, onValueChange: (String) -> Unit, placeholder: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, highlighted: Boolean = false) {
    val palette = LocalOdinPalette.current
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value, onValueChange = onValueChange, enabled = enabled,
        singleLine = true, textStyle = OdinTypography.body.copy(color = if (enabled) palette.text else palette.textMuted),
        cursorBrush = SolidColor(palette.accent),
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        decorationBox = { innerTextField ->
            OdinControlFrame(Modifier.fillMaxWidth(), focused = focused || highlighted, enabled = enabled) {
                if (value.isEmpty()) Text(placeholder, style = OdinTypography.body,
                    color = if (enabled) palette.textDim else palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                innerTextField()
            }
        }
    )
}
