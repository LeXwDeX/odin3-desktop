package com.odin.desktop.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.odin.desktop.ui.components.AppActionDialog
import com.odin.desktop.ui.components.AppBatchManageDialog
import com.odin.desktop.ui.components.AppSortMenu
import com.odin.desktop.ui.components.ConfigDialog
import com.odin.desktop.ui.viewmodel.LauncherViewModel
import com.odin.desktop.ui.state.collectAsVisibleState

@Composable
internal fun LauncherDialogRoutes(viewModel: LauncherViewModel) {
    LauncherConfigRoute(viewModel)
    LauncherAppActionRoute(viewModel)
    LauncherBatchManageRoute(viewModel)
    LauncherSortRoute(viewModel)
}

@Composable
private fun LauncherConfigRoute(viewModel: LauncherViewModel) {
    val isConfigOpen by viewModel.isConfigOpen.collectAsVisibleState()
    if (!isConfigOpen) return
    val configSectionIndex by viewModel.configSectionIndex.collectAsVisibleState()
    val configInSubMenu by viewModel.configInSubMenu.collectAsVisibleState()
    val configContentFocusIndex by viewModel.configContentFocusIndex.collectAsVisibleState()
    val configTabActionIndex by viewModel.configTabActionIndex.collectAsVisibleState()
    val joystickColor by viewModel.hardware.joystickColor.collectAsVisibleState()
    val orientationMode by viewModel.hardware.orientationMode.collectAsVisibleState()
    val isDefaultHome by viewModel.hardware.isDefaultHome.collectAsVisibleState()
    val appLanguage by viewModel.appLanguage.collectAsVisibleState()
    val tabs by viewModel.tabs.collectAsVisibleState()
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

}

@Composable
private fun LauncherAppActionRoute(viewModel: LauncherViewModel) {
    val isAppActionDialogOpen by viewModel.isAppActionDialogOpen.collectAsVisibleState()
    if (!isAppActionDialogOpen) return
    val appUnderAction by viewModel.appUnderAction.collectAsVisibleState()
    val tabs by viewModel.tabs.collectAsVisibleState()
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsVisibleState()
    val currentTab = tabs.getOrNull(selectedTabIndex)
    val appActionFocusIndex by viewModel.appActionFocusIndex.collectAsVisibleState()
    val appActionInTabPicker by viewModel.appActionInTabPicker.collectAsVisibleState()
    val appActionTabPickerFocusIndex by viewModel.appActionTabPickerFocusIndex.collectAsVisibleState()
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

}

@Composable
private fun LauncherBatchManageRoute(viewModel: LauncherViewModel) {
    val isAppBatchManageDialogOpen by viewModel.isAppBatchManageDialogOpen.collectAsVisibleState()
    if (!isAppBatchManageDialogOpen) return
    val tabs by viewModel.tabs.collectAsVisibleState()
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsVisibleState()
    val currentTab = tabs.getOrNull(selectedTabIndex)
    val allInstalledApps by viewModel.allInstalledApps.collectAsVisibleState()
    val currentTabAppPackages by viewModel.currentTabAppPackages.collectAsVisibleState()
    val batchManageSearchQuery by viewModel.batchManageSearchQuery.collectAsVisibleState()
    val batchManageFocusIndex by viewModel.batchManageFocusIndex.collectAsVisibleState()
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

@Composable
private fun LauncherSortRoute(viewModel: LauncherViewModel) {
    val open by viewModel.isSortMenuOpen.collectAsVisibleState()
    if (!open) return
    val sortMode by viewModel.sortMode.collectAsVisibleState()
    val index by viewModel.sortMenuIndex.collectAsVisibleState()
    val usageAvailable by viewModel.usageStatsAvailable.collectAsVisibleState()
    AppSortMenu(
        selected = sortMode, focusIndex = index, usageAvailable = usageAvailable,
        onSelect = viewModel::setSortMode, onDismiss = viewModel::closeSortMenu,
        onUsageAccess = viewModel::openUsageAccessSettings
    )
}
