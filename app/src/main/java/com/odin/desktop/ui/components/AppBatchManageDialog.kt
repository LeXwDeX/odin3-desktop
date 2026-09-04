package com.odin.desktop.ui.components

import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.data.model.displayName
import androidx.compose.ui.platform.LocalContext
import com.odin.desktop.R
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.odin.desktop.data.entity.TabEntity
import com.odin.desktop.data.model.InstalledApp
import com.odin.desktop.ui.components.base.ConsoleModalDialog

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
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text(strings.getString(R.string.text_filter_by_app_or_package_name_down), color = palette.textDim, fontSize = 13.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = palette.text,
                    unfocusedTextColor = palette.text,
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = if (isSearchFocused) palette.accent else palette.border,
                    focusedContainerColor = palette.background,
                    unfocusedContainerColor = palette.background
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSearchFocused) 2.dp else 0.dp,
                        color = if (isSearchFocused) palette.accent else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. 状态统计条
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.getString(R.string.text_matching_apps_value, filteredApps.size),
                    color = palette.textDim,
                    fontSize = 12.sp
                )
                Text(
                    text = strings.getString(R.string.text_this_category_contains_value_apps, currentTabAppPackages.size),
                    color = palette.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. 应用勾选列表
            if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(strings.getString(R.string.text_no_matching_apps), color = palette.textDim, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(filteredApps) { index, app ->
                        val isRowFocused = focusIndex == index
                        val isAdded = currentTabAppPackages.contains(app.packageName)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isRowFocused) palette.accent else palette.background)
                                .border(
                                    width = if (isRowFocused) 2.dp else 1.dp,
                                    color = if (isRowFocused) palette.accent else palette.border,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onToggleApp(app) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                AndroidView(
                                    factory = { context ->
                                        ImageView(context).apply {
                                            scaleType = ImageView.ScaleType.FIT_CENTER
                                        }
                                    },
                                    update = { imageView ->
                                        imageView.setImageDrawable(app.icon)
                                    },
                                    modifier = Modifier.size(28.dp)
                                )

                                Column {
                                    Text(
                                        text = app.label,
                                        color = if (isRowFocused) palette.background else palette.text,
                                        fontSize = 14.sp,
                                        fontWeight = if (isRowFocused) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        color = if (isRowFocused) palette.background.copy(alpha = 0.7f) else palette.textDim,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // 状态标签 / 勾选开关
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when {
                                            isRowFocused && isAdded -> palette.background
                                            isRowFocused && !isAdded -> palette.background.copy(alpha = 0.2f)
                                            isAdded -> palette.accent.copy(alpha = 0.2f)
                                            else -> palette.surface
                                        }
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isRowFocused) palette.background else (if (isAdded) palette.accent else palette.border),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isAdded) strings.getString(R.string.text_added) else strings.getString(R.string.text_not_added),
                                    color = when {
                                        isRowFocused && isAdded -> palette.accent
                                        isRowFocused && !isAdded -> palette.background
                                        isAdded -> palette.accent
                                        else -> palette.textDim
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
