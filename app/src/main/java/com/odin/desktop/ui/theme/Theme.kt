package com.odin.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes

@Composable
fun OdinDesktopTheme(
    palette: OdinPalette = OdinPalette(),
    typography: Typography = Typography(),
    shapes: Shapes = Shapes(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalOdinPalette provides palette) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = palette.accent,
                secondary = palette.active,
                background = palette.background,
                surface = palette.surface,
                onPrimary = palette.background,
                onBackground = palette.text,
                onSurface = palette.text
            ),
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}
