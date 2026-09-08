package com.odin.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.odin.desktop.R
import com.odin.desktop.data.model.HOME_APP_LIMIT
import com.odin.desktop.data.model.displayName
import com.odin.desktop.ui.components.AppActionDialog
import com.odin.desktop.ui.components.AppBatchManageDialog
import com.odin.desktop.ui.components.AppIconCollection
import com.odin.desktop.ui.components.AppSortMenu
import com.odin.desktop.ui.components.BottomDockBar
import com.odin.desktop.ui.components.ConfigDialog
import com.odin.desktop.ui.components.DashboardContent
import com.odin.desktop.ui.components.TopTabBar
import com.odin.desktop.ui.components.base.OdinActionButton
import com.odin.desktop.ui.navigation.FocusZone
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.theme.OdinSizes
import com.odin.desktop.ui.theme.OdinSpacing
import com.odin.desktop.ui.theme.OdinTypography
import com.odin.desktop.ui.viewmodel.LauncherViewModel

@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel,
    onOrientationChange: (Int) -> Unit
) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    val isDashboardSelected by viewModel.isDashboardSelected.collectAsState()
    val dashboardState by viewModel.dashboardState.collectAsState()
    val selectedDashboardControl by viewModel.selectedDashboardControl.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val tabs by viewModel.tabs.collectAsState()
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsState()
    val currentTab = tabs.getOrNull(selectedTabIndex)
    val isConfigFocused by viewModel.isConfigFocusedInTabs.collectAsState()
    val focusZone by viewModel.focusZone.collectAsState()

    val currentTabApps by viewModel.currentTabApps.collectAsState()
    val selectedAppIndex by viewModel.selectedAppIndex.collectAsState()

    val selectedDockIndex by viewModel.selectedDockIndex.collectAsState()
    val performanceMode by viewModel.hardware.performanceMode.collectAsState()
    val fanMode by viewModel.hardware.fanMode.collectAsState()
    val joystickLightEnabled by viewModel.hardware.joystickLightEnabled.collectAsState()
    val joystickColor by viewModel.hardware.joystickColor.collectAsState()
    val chargingSeparation by viewModel.hardware.chargingSeparation.collectAsState()
    val chargePowerLimit by viewModel.hardware.chargePowerLimit.collectAsState()
    val airplaneMode by viewModel.hardware.airplaneMode.collectAsState()
    val orientationMode by viewModel.hardware.orientationMode.collectAsState()
    val isDefaultHome by viewModel.hardware.isDefaultHome.collectAsState()

    val isConfigOpen by viewModel.isConfigOpen.collectAsState()
    val configSectionIndex by viewModel.configSectionIndex.collectAsState()
    val configInSubMenu by viewModel.configInSubMenu.collectAsState()
    val configContentFocusIndex by viewModel.configContentFocusIndex.collectAsState()
    val configTabActionIndex by viewModel.configTabActionIndex.collectAsState()

    val isAppActionDialogOpen by viewModel.isAppActionDialogOpen.collectAsState()
    val appUnderAction by viewModel.appUnderAction.collectAsState()
    val appActionFocusIndex by viewModel.appActionFocusIndex.collectAsState()
    val appActionInTabPicker by viewModel.appActionInTabPicker.collectAsState()
    val appActionTabPickerFocusIndex by viewModel.appActionTabPickerFocusIndex.collectAsState()

    val isAppBatchManageDialogOpen by viewModel.isAppBatchManageDialogOpen.collectAsState()
    val batchManageFocusIndex by viewModel.batchManageFocusIndex.collectAsState()
    val batchManageSearchQuery by viewModel.batchManageSearchQuery.collectAsState()
    val currentTabAppPackages by viewModel.currentTabAppPackages.collectAsState()
    val allInstalledApps by viewModel.allInstalledApps.collectAsState()

    val isReorderingApps by viewModel.isReorderingApps.collectAsState()
    val pickedAppIndex by viewModel.pickedAppIndex.collectAsState()
    val isAllAppsOpen by viewModel.isAllAppsOpen.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val usageAvailable by viewModel.usageStatsAvailable.collectAsState()
    val isSortMenuOpen by viewModel.isSortMenuOpen.collectAsState()
    val sortMenuIndex by viewModel.sortMenuIndex.collectAsState()

    androidx.compose.runtime.LaunchedEffect(orientationMode) {
        if (orientationMode >= 0) onOrientationChange(orientationMode)
    }

    // 掌机控制台级绝对屏幕居中与零像素抖动布局体系
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        if (isDashboardSelected) {
            DashboardContent(
                state = dashboardState,
                selectedControl = selectedDashboardControl,
                hasFocus = focusZone == FocusZone.DASHBOARD,
                onAction = viewModel::onDashboardAction,
                modifier = Modifier.fillMaxSize().padding(top = OdinSizes.headerHeight(), bottom = OdinSizes.chromeHeight())
            )
        } else {
            val isMoreSelected = !isAllAppsOpen && !isReorderingApps &&
                selectedAppIndex == HOME_APP_LIMIT && currentTabApps.size > HOME_APP_LIMIT
            val hoveredApp = if (isMoreSelected) null else currentTabApps.getOrNull(selectedAppIndex)
            Column(
                Modifier.fillMaxSize().padding(
                    top = if (isAllAppsOpen) OdinSpacing.md else OdinSizes.headerHeight(),
                    bottom = if (isAllAppsOpen) OdinSpacing.xs else OdinSizes.chromeHeight()
                )
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = OdinSpacing.page)) {
                    Text(
                        text = if (isAllAppsOpen) strings.getString(R.string.app_library_count,
                                currentTab?.displayName(strings).orEmpty(), currentTabApps.size)
                            else if (isMoreSelected) currentTab?.displayName(strings).orEmpty()
                            else hoveredApp?.label ?: strings.getString(R.string.text_no_apps_in_this_category),
                        color = if (focusZone == FocusZone.APPS) palette.accent else palette.text,
                        style = OdinTypography.h1,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isAllAppsOpen) hoveredApp?.label ?: "" else hoveredApp?.packageName ?: "",
                        color = palette.textDim, style = OdinTypography.body, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AppIconCollection(
                        apps = currentTabApps, selectedIndex = selectedAppIndex,
                        hasFocus = focusZone == FocusZone.APPS,
                        isGrid = isAllAppsOpen, isReordering = isReorderingApps, pickedIndex = pickedAppIndex,
                        collectionKey = if (isAllAppsOpen) "library" else selectedTabIndex, sortKey = sortMode,
                        onClick = viewModel::onAppClick, onPick = viewModel::pickAppForDrag,
                        onMove = viewModel::moveDraggedApp, onDrop = viewModel::finishAppDrag,
                        onAllApps = viewModel::openAllApps, onColumns = viewModel::setGridColumns,
                        modifier = if (isAllAppsOpen) Modifier.fillMaxSize().padding(top = OdinSpacing.xs)
                            else Modifier.fillMaxWidth().height(164.dp)
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = OdinSpacing.page),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OdinSpacing.xs)
                ) {
                    Text(
                        strings.getString(if (isReorderingApps) R.string.app_order_hint else R.string.app_browse_hint),
                        color = palette.textDim, style = OdinTypography.caption,
                        modifier = Modifier.weight(1f)
                    )
                    if (isReorderingApps) OdinActionButton(strings.getString(R.string.app_order_done), viewModel::exitReorderMode, Modifier.widthIn(max = 132.dp)) else {
                        OdinActionButton(strings.getString(R.string.app_actions), viewModel::openAppActionDialog, Modifier.widthIn(max = 132.dp))
                        OdinActionButton(strings.getString(R.string.app_sort_button), viewModel::openSortMenu, Modifier.widthIn(max = 132.dp))
                    }
                    if (isAllAppsOpen) OdinActionButton(strings.getString(R.string.app_library_back_button), viewModel::onBack, Modifier.widthIn(max = 132.dp))
                }
            }
        }

        if (!isAllAppsOpen) {
        // 3. 顶部 Tab 栏 (顶部对齐)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            TopTabBar(
                telemetry = telemetry,
                tabs = tabs,
                isDashboardSelected = isDashboardSelected,
                onDashboardSelected = viewModel::selectDashboard,
                selectedTabIndex = selectedTabIndex,
                isConfigFocused = isConfigFocused,
                focusZone = focusZone,
                onTabSelected = { index ->
                    viewModel.selectTab(index)
                },
                onConfigClick = {
                    viewModel.openConfigDialog()
                }
            )
        }

        // 4. 底部 5 大硬件状态控制 Dock (底部对齐，固定高度 58dp)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            BottomDockBar(
                performanceMode = performanceMode,
                fanMode = fanMode,
                joystickLightEnabled = joystickLightEnabled,
                chargingSeparation = chargingSeparation,
                chargePowerLimit = chargePowerLimit,
                airplaneMode = airplaneMode,
                selectedDockIndex = selectedDockIndex,
                focusZone = focusZone,
                onItemClick = { index ->
                    viewModel.onDockItemClick(index)
                }
            )
        }
        }
        if (isSortMenuOpen) AppSortMenu(
            selected = sortMode, focusIndex = sortMenuIndex, usageAvailable = usageAvailable,
            onSelect = viewModel::setSortMode, onDismiss = viewModel::closeSortMenu,
            onUsageAccess = viewModel::openUsageAccessSettings
        )
    }

    // 5. Config 设置弹窗 (支持手柄 D-Pad 上下左右与 A/B/X/L1/R1 盲操)
    val appLanguage by viewModel.appLanguage.collectAsState()
    ConfigDialog(
        isOpen = isConfigOpen,
        onDismiss = { viewModel.closeConfigDialog() },
        selectedSection = configSectionIndex,
        inSubMenu = configInSubMenu,
        subFocusIndex = configContentFocusIndex,
        onSectionClick = { index -> viewModel.setConfigSection(index) },
        currentJoystickColor = joystickColor,
        currentOrientation = orientationMode,
        currentLanguage = appLanguage,
        onLanguageSelect = viewModel::setAppLanguage,
        isDefaultHome = isDefaultHome,
        onColorSelect = { hex -> viewModel.hardware.setJoystickColor(hex) },
        onOrientationSelect = { mode -> viewModel.hardware.setOrientationMode(mode) },
        onRequestDefaultHome = { viewModel.hardware.requestDefaultHome() },
        tabs = tabs,
        tabActionFocusIndex = configTabActionIndex,
        onAddTab = { name, isGame -> viewModel.addTab(name, isGame) },
        onRenameTab = { tab, name -> viewModel.renameTab(tab, name) },
        onDeleteTab = { tab -> viewModel.deleteTab(tab) },
        onMoveTabUp = { tab -> viewModel.moveTabUp(tab) },
        onMoveTabDown = { tab -> viewModel.moveTabDown(tab) },
        onSetDefaultTab = { tab -> viewModel.setDefaultHomeTab(tab) }
    )

    // 6. 对着应用按 Y 键呼出【应用专属操作】模态框
    AppActionDialog(
        isOpen = isAppActionDialogOpen,
        app = appUnderAction,
        currentTab = currentTab,
        allTabs = tabs,
        focusIndex = appActionFocusIndex,
        inTabPicker = appActionInTabPicker,
        tabPickerFocusIndex = appActionTabPickerFocusIndex,
        onDismiss = { viewModel.closeAppActionDialog() },
        onExecuteAction = { type -> viewModel.executeAppAction(type) },
        onMoveToTab = { tab ->
            appUnderAction?.let { viewModel.moveAppToTab(it, tab.id) }
        }
    )

    // 7. 长按 X 键呼出【批量增删分类应用】模态框
    AppBatchManageDialog(
        isOpen = isAppBatchManageDialogOpen,
        currentTab = currentTab,
        allApps = allInstalledApps,
        currentTabAppPackages = currentTabAppPackages,
        searchQuery = batchManageSearchQuery,
        focusIndex = batchManageFocusIndex,
        onSearchChange = { query -> viewModel.setBatchManageSearchQuery(query) },
        onDismiss = { viewModel.closeBatchManageDialog() },
        onToggleApp = { app -> viewModel.toggleAppInCurrentTab(app) }
    )
}
