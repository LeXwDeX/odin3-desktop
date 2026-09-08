package com.odin.desktop.ui.components

import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.odin.desktop.R
import com.odin.desktop.data.entity.TabEntity
import com.odin.desktop.data.model.InstalledApp
import com.odin.desktop.data.model.displayName
import com.odin.desktop.ui.components.base.BadgeRole
import com.odin.desktop.ui.components.base.ConsoleModalDialog
import com.odin.desktop.ui.components.base.OdinControl
import com.odin.desktop.ui.components.base.OdinTextField
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.theme.OdinSizes
import com.odin.desktop.ui.theme.OdinSpacing
import com.odin.desktop.ui.theme.OdinTypography

@Composable
fun AppBatchManageDialog(
    isOpen: Boolean,
    currentTab: TabEntity?,
    allApps: List<InstalledApp>,
    currentTabAppPackages: Set<String>,
    searchQuery: String,
    focusIndex: Int, // -1 = 搜索框, 0..N = 列表项
    onSearchChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onToggleApp: (InstalledApp) -> Unit
) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    if (!isOpen || currentTab == null) return

    val filteredApps = remember(allApps, searchQuery) {
        if (searchQuery.isBlank()) allApps
        else {
            val q = searchQuery.trim().lowercase()
            allApps.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
    }

    val listState = rememberLazyListState()

    // 焦点自动滚动到可视区域
    LaunchedEffect(focusIndex) {
        if (focusIndex in filteredApps.indices) {
            listState.animateScrollToItem(focusIndex)
        }
    }

    ConsoleModalDialog(
        isOpen = isOpen,
        onDismissRequest = onDismiss,
        title = strings.getString(R.string.text_manage_category_apps),
        badgeText = currentTab.displayName(strings),
        footerHint = strings.getString(R.string.text_up_down_select_app_a_add_remove),
        maxWidth = 680.dp,
        maxHeight = 440.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. 顶部即时搜索框
            val isSearchFocused = focusIndex == -1
            OdinTextField(searchQuery, onSearchChange,
                strings.getString(R.string.text_filter_by_app_or_package_name_down),
                modifier = Modifier.fillMaxWidth(), highlighted = isSearchFocused)

            Spacer(modifier = Modifier.height(OdinSpacing.md))

            // 2. 状态统计条
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.getString(R.string.text_matching_apps_value, filteredApps.size),
                    color = palette.textDim,
                    style = OdinTypography.caption
                )
                Text(
                    text = strings.getString(R.string.text_this_category_contains_value_apps, currentTabAppPackages.size),
                    color = palette.accent,
                    style = OdinTypography.caption
                )
            }

            Spacer(modifier = Modifier.height(OdinSpacing.sm))

            // 3. 应用勾选列表
            if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(strings.getString(R.string.text_no_matching_apps), color = palette.textDim, style = OdinTypography.body)
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(OdinSpacing.sm),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(filteredApps) { index, app ->
                        val isRowFocused = focusIndex == index
                        val isAdded = currentTabAppPackages.contains(app.packageName)

                        OdinControl(
                            text = app.label, subtitle = app.packageName,
                            onClick = { onToggleApp(app) }, focused = isRowFocused, selected = isAdded,
                            toggle = true,
                            badge = strings.getString(if (isAdded) R.string.text_added else R.string.text_not_added),
                            badgeRole = if (isAdded) BadgeRole.ACTIVE else BadgeRole.NEUTRAL,
                            modifier = Modifier.fillMaxWidth(),
                            icon = {
                                AndroidView(factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                                    update = { it.setImageDrawable(app.icon) }, modifier = Modifier.size(OdinSizes.imageIcon))
                            }
                        )
                    }
                }
            }
        }
    }
}
