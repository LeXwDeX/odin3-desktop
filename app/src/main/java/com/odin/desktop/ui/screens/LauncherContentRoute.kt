package com.odin.desktop.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.odin.desktop.R
import com.odin.desktop.data.model.HOME_APP_LIMIT
import com.odin.desktop.data.model.displayName
import com.odin.desktop.ui.components.AppIconCollection
import com.odin.desktop.ui.components.base.OdinStableTextLine
import com.odin.desktop.ui.components.DashboardContent
import com.odin.desktop.ui.components.base.OdinActionButton
import com.odin.desktop.ui.components.base.OdinEqualHeightRow
import com.odin.desktop.ui.components.base.OdinSymbol
import com.odin.desktop.ui.components.base.OdinSymbolIcon
import com.odin.desktop.ui.navigation.FocusZone
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.theme.OdinSizes
import com.odin.desktop.ui.theme.OdinSpacing
import com.odin.desktop.ui.theme.OdinTypography
import com.odin.desktop.ui.viewmodel.LauncherViewModel
import com.odin.desktop.ui.state.collectAsVisibleState

@Composable
internal fun LauncherContentRoute(viewModel: LauncherViewModel) {
    val dashboardSelected by viewModel.isDashboardSelected.collectAsVisibleState()
    if (dashboardSelected) LauncherDashboardRoute(viewModel) else LauncherAppsRoute(viewModel)
}

@Composable
private fun LauncherDashboardRoute(viewModel: LauncherViewModel) {
    val state by viewModel.dashboardState.collectAsVisibleState()
    val selectedControl by viewModel.selectedDashboardControl.collectAsVisibleState()
    val focusZone by viewModel.focusZone.collectAsVisibleState()
    DashboardContent(
        state = state, selectedControl = selectedControl,
        hasFocus = focusZone == FocusZone.DASHBOARD,
        onAction = viewModel::onDashboardAction,
        modifier = Modifier.fillMaxSize().padding(top = OdinSizes.headerHeight(), bottom = OdinSizes.chromeHeight())
    )
}

@Composable
private fun LauncherAppsRoute(viewModel: LauncherViewModel) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    val tabs by viewModel.tabs.collectAsVisibleState()
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsVisibleState()
    val currentTab = tabs.getOrNull(selectedTabIndex)
    val focusZone by viewModel.focusZone.collectAsVisibleState()
    val currentTabApps by viewModel.currentTabApps.collectAsVisibleState()
    val selectedAppIndex by viewModel.selectedAppIndex.collectAsVisibleState()
    val isReorderingApps by viewModel.isReorderingApps.collectAsVisibleState()
    val pickedAppIndex by viewModel.pickedAppIndex.collectAsVisibleState()
    val isAllAppsOpen by viewModel.isAllAppsOpen.collectAsVisibleState()
    val sortMode by viewModel.sortMode.collectAsVisibleState()
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
            OdinStableTextLine(
                text = if (isAllAppsOpen) strings.resources.getQuantityString(R.plurals.app_library_count,
                        currentTabApps.size, currentTab?.displayName(strings).orEmpty(), currentTabApps.size)
                    else if (isMoreSelected) currentTab?.displayName(strings).orEmpty()
                    else hoveredApp?.label ?: strings.getString(R.string.text_no_apps_in_this_category),
                color = palette.text,
                style = OdinTypography.h1
            )
            OdinStableTextLine(
                text = if (isAllAppsOpen) hoveredApp?.label ?: ""
                    else strings.resources.getQuantityString(R.plurals.app_library_count,
                        currentTabApps.size, currentTab?.displayName(strings).orEmpty(), currentTabApps.size),
                color = palette.textDim, style = OdinTypography.body
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
        OdinEqualHeightRow(Modifier.padding(horizontal = OdinSpacing.page)) {
            Text(
                strings.getString(if (isReorderingApps) R.string.app_order_hint else R.string.app_browse_hint),
                color = palette.textDim, style = OdinTypography.caption,
                modifier = Modifier.weight(1f)
            )
            if (isReorderingApps) OdinActionButton(strings.getString(R.string.app_order_done), viewModel::exitReorderMode, Modifier.widthIn(max = 132.dp).fillMaxHeight()) else {
                OdinActionButton(strings.getString(R.string.app_actions), viewModel::openAppActionDialog,
                    Modifier.widthIn(max = 132.dp).fillMaxHeight(), icon = { OdinSymbolIcon(OdinSymbol.MANAGE) })
                OdinActionButton(strings.getString(R.string.app_sort_button), viewModel::openSortMenu,
                    Modifier.widthIn(max = 132.dp).fillMaxHeight(), icon = { OdinSymbolIcon(OdinSymbol.SORT) })
            }
            if (isAllAppsOpen) OdinActionButton(strings.getString(R.string.app_library_back_button), viewModel::onBack, Modifier.widthIn(max = 132.dp).fillMaxHeight())
        }
    }
}
