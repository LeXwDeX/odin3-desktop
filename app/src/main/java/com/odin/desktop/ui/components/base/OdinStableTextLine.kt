package com.odin.desktop.ui.components.base

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight

/** A single line whose slot and baseline do not depend on fallback glyph metrics. */
@Composable
fun OdinStableTextLine(text: String, color: Color, style: TextStyle, modifier: Modifier = Modifier) {
    Layout(modifier = modifier, content = {
        Text(text, color = color, style = style, maxLines = 1, softWrap = false,
            overflow = TextOverflow.Ellipsis)
    }) { measurables, constraints ->
        // Keep font scaling, but never let CJK/emoji fallback or an empty label resize
        // the parent Column and move the collection centered in its remaining space.
        val height = constraints.constrainHeight(style.lineHeight.roundToPx())
        val baseline = style.fontSize.roundToPx()
        val line = measurables.single().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        layout(line.width, height, mapOf(FirstBaseline to baseline, LastBaseline to baseline)) {
            line.placeRelative(0, baseline - line[FirstBaseline])
        }
    }
}
