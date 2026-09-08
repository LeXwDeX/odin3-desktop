package com.odin.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.odin.desktop.R
import com.odin.desktop.data.model.AppSortMode
import com.odin.desktop.ui.components.base.ConsoleDialogItem
import com.odin.desktop.ui.components.base.ConsoleModalDialog
import com.odin.desktop.ui.components.base.OdinActionButton
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.theme.OdinSpacing
import com.odin.desktop.ui.theme.OdinTypography

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
    ConsoleModalDialog(isOpen = true, onDismissRequest = onDismiss,
        title = stringResource(R.string.app_sort_title), maxWidth = 560.dp) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(OdinSpacing.sm)) {
            AppSortMode.entries.forEachIndexed { index, mode ->
                val unavailable = mode == AppSortMode.LAST_USED && !usageAvailable
                ConsoleDialogItem(title = stringResource(appSortLabel(mode)),
                    subtitle = if (unavailable) stringResource(R.string.app_usage_access) else null,
                    isFocused = index == focusIndex, isSelected = selected == mode, radio = true,
                    trailingText = if (selected == mode) stringResource(R.string.text_active) else null,
                    onClick = { if (unavailable) onUsageAccess() else onSelect(mode) })
            }
            Text(stringResource(R.string.app_sort_detail), color = palette.textDim, style = OdinTypography.caption)
            OdinActionButton(stringResource(R.string.app_library_close), onDismiss, Modifier.fillMaxWidth())
        }
    }
}
