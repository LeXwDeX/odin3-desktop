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
    typography: Typography = OdinTypography.material,
    shapes: Shapes = OdinMaterialShapes,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalOdinPalette provides palette) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = palette.accent,
                secondary = palette.active,
                tertiary = palette.special,
                background = palette.background,
                surface = palette.surface,
                surfaceVariant = palette.card,
                primaryContainer = palette.selection,
                outline = palette.border,
                outlineVariant = palette.border,
                error = palette.danger,
                onPrimary = palette.background,
                onSecondary = palette.background,
                onTertiary = palette.background,
                onPrimaryContainer = palette.text,
                onError = palette.background,
                onBackground = palette.text,
                onSurface = palette.text,
                onSurfaceVariant = palette.textDim
            ),
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}
