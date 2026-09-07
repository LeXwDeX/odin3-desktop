package com.odin.desktop.ui.components

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
import androidx.compose.ui.unit.sp
import com.odin.desktop.R
import com.odin.desktop.data.model.AppSortMode
import com.odin.desktop.ui.theme.LocalOdinPalette

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
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(20.dp)
            .background(palette.surface, RoundedCornerShape(16.dp)).clickable { }
            .padding(20.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.app_sort_title), fontSize = 22.sp, color = palette.text)
            Spacer(Modifier.height(12.dp))
            AppSortMode.entries.forEachIndexed { index, mode ->
                val unavailable = mode == AppSortMode.LAST_USED && !usageAvailable
                Column(Modifier.fillMaxWidth().border(1.dp,
                    if (index == focusIndex) palette.accent else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { if (unavailable) onUsageAccess() else onSelect(mode) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text((if (selected == mode) "✓  " else "    ") + stringResource(appSortLabel(mode)),
                        color = if (unavailable) palette.textDim else palette.text, fontSize = 16.sp)
                    if (unavailable) Text(stringResource(R.string.app_usage_access), color = palette.textDim, fontSize = 12.sp)
                }
            }
            Text(stringResource(R.string.app_sort_detail), color = palette.textDim, fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.app_library_close), color = palette.accent)
            }
        }
    }
}
