package com.odin.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    secondary = GreenActive,
    background = PureBlack,
    surface = DarkSurface,
    onPrimary = PureBlack,
    onBackground = TextWhite,
    onSurface = TextWhite
)

@Composable
fun OdinDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
