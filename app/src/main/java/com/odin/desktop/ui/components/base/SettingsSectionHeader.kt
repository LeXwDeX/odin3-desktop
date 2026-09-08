package com.odin.desktop.ui.components.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.theme.OdinSpacing
import com.odin.desktop.ui.theme.OdinTypography

@Composable
internal fun SettingsSectionHeader(
    title: String,
    description: String? = null,
    descriptionColor: Color = LocalOdinPalette.current.textDim,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(OdinSpacing.xs)) {
        Text(title, style = OdinTypography.h2, color = LocalOdinPalette.current.text,
            modifier = Modifier.semantics { heading() })
        if (description != null) Text(description, style = OdinTypography.body, color = descriptionColor)
    }
}
