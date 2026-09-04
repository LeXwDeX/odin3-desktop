package com.odin.desktop.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colors for the launcher. Skin adapters supply values, never navigation or hardware code. */
@Immutable
data class OdinPalette(
    val background: Color = PureBlack,
    val surface: Color = DarkSurface,
    val card: Color = CardBackground,
    val border: Color = CardBorder,
    val accent: Color = CyanAccent,
    val accentGlow: Color = CyanAccentGlow,
    val text: Color = TextWhite,
    val textDim: Color = TextDim,
    val textMuted: Color = TextDarkDim,
    val active: Color = GreenActive,
    val warning: Color = OrangeWarning,
    val danger: Color = RedDanger,
    val special: Color = BlueSpecial
)

val LocalOdinPalette = staticCompositionLocalOf { OdinPalette() }
