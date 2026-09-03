package com.odin.desktop.ui.components

import android.widget.ImageView
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.odin.desktop.data.entity.TabEntity
import com.odin.desktop.data.model.InstalledApp
import com.odin.desktop.ui.components.base.ConsoleDialogItem
import com.odin.desktop.ui.components.base.ConsoleModalDialog
import com.odin.desktop.ui.theme.CardBackground
import com.odin.desktop.ui.theme.CyanAccent
import com.odin.desktop.ui.theme.DarkSurface
import com.odin.desktop.ui.theme.RedDanger
import com.odin.desktop.ui.theme.TextDim
import com.odin.desktop.ui.theme.TextWhite

enum class AppActionType {
    MOVE_TO_TAB,
    APP_DETAILS,
    REMOVE_ICON
}

data class AppActionItem(
    val type: AppActionType,
    val title: String,
    val subtitle: String,
    val isDanger: Boolean = false,
    val isEnabled: Boolean = true
)

@Composable
fun AppActionDialog(
    isOpen: Boolean,
    app: InstalledApp?,
    currentTab: TabEntity?,
    allTabs: List<TabEntity>,
    focusIndex: Int,
    inTabPicker: Boolean,
    tabPickerFocusIndex: Int,
    onDismiss: () -> Unit,
    onExecuteAction: (AppActionType) -> Unit,
    onMoveToTab: (TabEntity) -> Unit
) {
    if (!isOpen || app == null) return

    val targetTabs = allTabs.filter { it.id != currentTab?.id && it.name != "全部应用" }
    val isAllAppsTab = currentTab == null || currentTab.name == "全部应用"

    val actions = listOf(
        AppActionItem(
            type = AppActionType.MOVE_TO_TAB,
            title = "📁 移动至其他 Tab 分类",
            subtitle = if (targetTabs.isEmpty()) "暂无其他可移动的自定义分类" else "将图标分配至其他分类 Tab",
            isEnabled = targetTabs.isNotEmpty()
        ),
        AppActionItem(
            type = AppActionType.APP_DETAILS,
            title = "⚙️ 进入应用属性详情 (系统设置)",
            subtitle = "可在此停止运行、卸载应用、管理存储与权限"
        ),
        AppActionItem(
            type = AppActionType.REMOVE_ICON,
            title = "🗑️ 从当前分类移除",
            subtitle = if (isAllAppsTab) "【全部应用】为系统全集分类，无法直接移除图标" else "仅从当前 Tab 移除，不影响应用安装状态",
            isDanger = true,
            isEnabled = !isAllAppsTab
        )
    )

    val listState = rememberLazyListState()
    val tabPickerListState = rememberLazyListState()

    // 手柄选择时平滑滚动 view，保证光标位置在 view 中始终可见
    LaunchedEffect(focusIndex) {
        if (focusIndex in actions.indices) {
            listState.animateScrollToItem(focusIndex)
        }
    }

    LaunchedEffect(tabPickerFocusIndex) {
        if (tabPickerFocusIndex in targetTabs.indices) {
            tabPickerListState.animateScrollToItem(tabPickerFocusIndex)
        }
    }

    ConsoleModalDialog(
        isOpen = isOpen,
        onDismissRequest = onDismiss,
        title = app.label,
        titleIcon = {
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
        },
        badgeText = if (inTabPicker) "选择目标 Tab" else (currentTab?.name ?: "全部应用"),
        footerHint = if (inTabPicker) "【上下键选目标 Tab • A 键确认移动 • B 键返回上一层】" else "【上下键选择 • A 键执行 • B 键关闭】",
        maxWidth = 660.dp,
        maxHeight = 420.dp
    ) {
        if (inTabPicker) {
            // 选择目标 Tab 子面板
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "请选择要将 \"${app.label}\" 移动到的目标 Tab：",
                    color = TextDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                LazyColumn(
                    state = tabPickerListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(targetTabs) { index, tab ->
                        val isFocused = index == tabPickerFocusIndex
                        ConsoleDialogItem(
                            title = "📁 ${tab.name}",
                            subtitle = if (tab.isGameTab) "游戏模拟器分类" else "常规分类",
                            isFocused = isFocused,
                            trailingText = if (tab.isDefault) "★ 默认首页" else null,
                            onClick = { onMoveToTab(tab) }
                        )
                    }
                }
            }
        } else {
            // 一级应用操作列表
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(actions) { index, item ->
                    val isFocused = index == focusIndex
                    val context = LocalContext.current
                    ConsoleDialogItem(
                        title = item.title,
                        subtitle = item.subtitle,
                        isFocused = isFocused,
                        isDanger = item.isDanger,
                        onClick = {
                            if (item.isEnabled) {
                                onExecuteAction(item.type)
                            } else {
                                Toast.makeText(context, item.subtitle, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}
