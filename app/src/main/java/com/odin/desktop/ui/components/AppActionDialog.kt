package com.odin.desktop.ui.components

import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.odin.desktop.R
import com.odin.desktop.data.entity.TabEntity
import com.odin.desktop.data.model.InstalledApp
import com.odin.desktop.data.model.displayName
import com.odin.desktop.ui.components.base.ConsoleDialogItem
import com.odin.desktop.ui.components.base.ConsoleModalDialog
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.theme.OdinSpacing
import com.odin.desktop.ui.theme.OdinTypography

enum class AppActionType {
    MOVE_TO_TAB,
    APP_DETAILS,
    REMOVE_ICON
}

fun getAvailableAppActions(currentTab: TabEntity?, allTabs: List<TabEntity>): List<AppActionType> = buildList {
    if (allTabs.any { it.id != currentTab?.id && it.kind != com.odin.desktop.data.entity.TabKind.ALL_APPS })
        add(AppActionType.MOVE_TO_TAB)
    add(AppActionType.APP_DETAILS)
    if (currentTab != null && currentTab.kind != com.odin.desktop.data.entity.TabKind.ALL_APPS)
        add(AppActionType.REMOVE_ICON)
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
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    if (!isOpen || app == null) return

    val targetTabs = allTabs.filter { it.id != currentTab?.id && it.kind != com.odin.desktop.data.entity.TabKind.ALL_APPS }
    val isAllAppsTab = currentTab == null || currentTab.kind == com.odin.desktop.data.entity.TabKind.ALL_APPS

    val available = getAvailableAppActions(currentTab, allTabs)
    val actions = listOf(
        AppActionItem(
            type = AppActionType.MOVE_TO_TAB,
            title = strings.getString(R.string.text_move_to_another_tab),
            subtitle = if (targetTabs.isEmpty()) strings.getString(R.string.text_no_other_custom_categories_are_available) else strings.getString(R.string.text_assign_this_icon_to_another_category_tab),
            isEnabled = AppActionType.MOVE_TO_TAB in available
        ),
        AppActionItem(
            type = AppActionType.APP_DETAILS,
            title = strings.getString(R.string.text_app_details_system_settings),
            subtitle = strings.getString(R.string.text_stop_or_uninstall_the_app_or_manage)
        ),
        AppActionItem(
            type = AppActionType.REMOVE_ICON,
            title = strings.getString(R.string.text_remove_from_this_category),
            subtitle = if (isAllAppsTab) strings.getString(R.string.text_all_apps_contains_every_installed_app_icons) else strings.getString(R.string.text_remove_from_this_tab_keep_the_app),
            isDanger = true,
            isEnabled = AppActionType.REMOVE_ICON in available
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
                modifier = Modifier.size(com.odin.desktop.ui.theme.OdinSizes.imageIcon)
            )
        },
        badgeText = if (inTabPicker) strings.getString(R.string.text_choose_target_tab) else (currentTab?.displayName(strings) ?: strings.getString(R.string.text_all_apps)),
        footerHint = if (inTabPicker) strings.getString(R.string.text_up_down_target_tab_a_move_b) else strings.getString(R.string.text_up_down_select_a_execute_b_close),
        maxWidth = 660.dp,
        maxHeight = 420.dp
    ) {
        if (inTabPicker) {
            // 选择目标 Tab 子面板
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = strings.getString(R.string.text_choose_a_target_tab_for_value, app.label),
                    color = palette.textDim,
                    style = OdinTypography.body,
                    modifier = Modifier.padding(bottom = OdinSpacing.sm)
                )

                LazyColumn(
                    state = tabPickerListState,
                    verticalArrangement = Arrangement.spacedBy(OdinSpacing.sm),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(targetTabs) { index, tab ->
                        val isFocused = index == tabPickerFocusIndex
                        ConsoleDialogItem(
                            title = "📁 ${tab.displayName(strings)}",
                            subtitle = if (tab.isGameTab) strings.getString(R.string.text_games_and_emulators) else strings.getString(R.string.text_general_category),
                            isFocused = isFocused,
                        radio = true,
                            trailingText = if (tab.isDefault) strings.getString(R.string.text_home_tab) else null,
                            onClick = { onMoveToTab(tab) }
                        )
                    }
                }
            }
        } else {
            // 一级应用操作列表
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(OdinSpacing.sm),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(actions) { index, item ->
                    val isFocused = index == focusIndex
                    ConsoleDialogItem(
                        title = item.title,
                        subtitle = item.subtitle,
                        isFocused = isFocused,
                        enabled = item.isEnabled,
                        isDanger = item.isDanger,
                        onClick = { onExecuteAction(item.type) }
                    )
                }
            }
        }
    }
}
