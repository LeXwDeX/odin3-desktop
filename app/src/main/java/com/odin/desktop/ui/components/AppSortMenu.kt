package com.odin.desktop.ui.components

import com.odin.desktop.ui.theme.OdinCorners
import com.odin.desktop.ui.theme.OdinInsets
import com.odin.desktop.ui.theme.OdinSpacing
import com.odin.desktop.ui.theme.OdinTypography
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.odin.desktop.R
import com.odin.desktop.data.model.AppSortMode
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.components.base.ConsoleDialogItem

fun appSortLabel(mode: AppSortMode): Int = when (mode) {
    AppSortMode.MANUAL -> R.string.app_sort_manual
    AppSortMode.INSTALLED -> R.string.app_sort_installed
    AppSortMode.LAST_USED -> R.string.app_sort_last_used
    AppSortMode.NAME -> R.string.app_sort_name
}

@Composable
fun AppSortMenu(selected: AppSortMode, focusIndex: Int, usageAvailable: Boolean,
    onSelect: (AppSortMode) -> Unit, onDismiss: () -> Unit, onUsageAccess: () -> Unit) {
    val palette = LocalOdinPalette.current
    Box(Modifier.fillMaxSize().background(palette.background.copy(alpha = 0.88f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(OdinSpacing.panel)
            .background(palette.surface, RoundedCornerShape(OdinCorners.dialog))
            .border(1.dp, palette.border, RoundedCornerShape(OdinCorners.dialog)).clickable { }
            .padding(OdinSpacing.panel).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.app_sort_title), style = OdinTypography.h2, color = palette.text)
            Spacer(Modifier.height(OdinSpacing.lg))
            AppSortMode.entries.forEachIndexed { index, mode ->
                val unavailable = mode == AppSortMode.LAST_USED && !usageAvailable
                if (index > 0) Spacer(Modifier.height(OdinSpacing.sm))
                ConsoleDialogItem(
                    title = stringResource(appSortLabel(mode)),
                    subtitle = if (unavailable) stringResource(R.string.app_usage_access) else null,
                    isFocused = index == focusIndex,
                    isSelected = selected == mode,
                    trailingText = if (selected == mode) stringResource(R.string.text_active) else null,
                    onClick = { if (unavailable) onUsageAccess() else onSelect(mode) }
                )
            }
            Text(stringResource(R.string.app_sort_detail), color = palette.textDim, style = OdinTypography.body,
                modifier = Modifier.padding(top = OdinSpacing.lg))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.Start),
                contentPadding = OdinInsets.button) {
                Text(stringResource(R.string.app_library_close), color = palette.accent, style = OdinTypography.button)
            }
        }
    }
}
