package com.odin.desktop.ui.components

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
import androidx.compose.runtime.mutableStateOf
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
import com.odin.desktop.ui.theme.CardBackground
import com.odin.desktop.ui.theme.CardBorder
import com.odin.desktop.ui.theme.CyanAccent
import com.odin.desktop.ui.theme.DarkSurface
import com.odin.desktop.ui.theme.PureBlack
import com.odin.desktop.ui.theme.TextDim
import com.odin.desktop.ui.theme.TextWhite

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
        title = "增删分类应用",
        badgeText = currentTab.name,
        footerHint = "【上下键选应用 • A 键勾选/移除 • B 键完成】",
        maxWidth = 680.dp,
        maxHeight = 440.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. 顶部即时搜索框
            val isSearchFocused = focusIndex == -1
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("输入应用名或包名即时过滤 (手柄下键可切回列表)...", color = TextDim, fontSize = 13.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedBorderColor = CyanAccent,
                    unfocusedBorderColor = if (isSearchFocused) CyanAccent else CardBorder,
                    focusedContainerColor = PureBlack,
                    unfocusedContainerColor = PureBlack
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSearchFocused) 2.dp else 0.dp,
                        color = if (isSearchFocused) CyanAccent else Color.Transparent,
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
                    text = "匹配应用 (${filteredApps.size} 项)：",
                    color = TextDim,
                    fontSize = 12.sp
                )
                Text(
                    text = "当前分类已包含 ${currentTabAppPackages.size} 个应用",
                    color = CyanAccent,
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
                    Text("未匹配到符合条件的应用", color = TextDim, fontSize = 14.sp)
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
                                .background(if (isRowFocused) CyanAccent else PureBlack)
                                .border(
                                    width = if (isRowFocused) 2.dp else 1.dp,
                                    color = if (isRowFocused) CyanAccent else CardBorder,
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
                                        color = if (isRowFocused) PureBlack else TextWhite,
                                        fontSize = 14.sp,
                                        fontWeight = if (isRowFocused) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        color = if (isRowFocused) PureBlack.copy(alpha = 0.7f) else TextDim,
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
                                            isRowFocused && isAdded -> PureBlack
                                            isRowFocused && !isAdded -> PureBlack.copy(alpha = 0.2f)
                                            isAdded -> CyanAccent.copy(alpha = 0.2f)
                                            else -> DarkSurface
                                        }
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isRowFocused) PureBlack else (if (isAdded) CyanAccent else CardBorder),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isAdded) "✓ 已加入" else "+ 未加入",
                                    color = when {
                                        isRowFocused && isAdded -> CyanAccent
                                        isRowFocused && !isAdded -> PureBlack
                                        isAdded -> CyanAccent
                                        else -> TextDim
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
