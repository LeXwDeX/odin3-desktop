package com.odin.desktop.ui.components.base

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.theme.OdinSizes
import com.odin.desktop.ui.theme.OdinTypography

/** Primary information above its muted supporting line, shared by all header readings. */
@Composable
fun OdinStatusReadout(primary: String, supporting: String, modifier: Modifier = Modifier) {
    val palette = LocalOdinPalette.current
    val lineHeight = OdinSizes.readoutLineHeight()
    val baseline = with(LocalDensity.current) { OdinTypography.caption.lineHeight.toDp() }
    // A shared 24 sp line slot leaves room below the baseline for enlarged CJK fallback glyphs.
    val line = Modifier.height(lineHeight).paddingFromBaseline(top = baseline)
    Column(modifier) {
        Text(primary, modifier = line, color = palette.text, style = OdinTypography.caption, maxLines = 1, softWrap = false)
        Text(supporting, modifier = line, color = palette.textDim, style = OdinTypography.caption, maxLines = 1, softWrap = false)
    }
}
