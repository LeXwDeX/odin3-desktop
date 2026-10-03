package com.odin.desktop.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.odin.desktop.ui.components.BottomDockBar
import com.odin.desktop.ui.components.TopTabBar
import com.odin.desktop.ui.viewmodel.LauncherViewModel
import com.odin.desktop.ui.state.collectAsVisibleState

/** Telemetry updates are confined to the header; hardware state is confined to the Dock. */
@Composable
internal fun LauncherHeaderRoute(viewModel: LauncherViewModel, modifier: Modifier = Modifier) {
    val telemetry by viewModel.telemetry.collectAsVisibleState()
    val tabs by viewModel.tabs.collectAsVisibleState()
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsVisibleState()
    val dashboardSelected by viewModel.isDashboardSelected.collectAsVisibleState()
    val configFocused by viewModel.isConfigFocusedInTabs.collectAsVisibleState()
    val focusZone by viewModel.focusZone.collectAsVisibleState()
    TopTabBar(
        telemetry = telemetry, tabs = tabs, selectedTabIndex = selectedTabIndex,
        isDashboardSelected = dashboardSelected, onDashboardSelected = viewModel::selectDashboard,
        isConfigFocused = configFocused, focusZone = focusZone,
        onTabSelected = viewModel::selectTab, onConfigClick = viewModel::openConfigDialog,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
internal fun LauncherDockRoute(viewModel: LauncherViewModel, modifier: Modifier = Modifier) {
    val performance by viewModel.hardware.performanceMode.collectAsVisibleState()
    val fan by viewModel.hardware.fanMode.collectAsVisibleState()
    val lights by viewModel.hardware.joystickLightEnabled.collectAsVisibleState()
    val separation by viewModel.hardware.chargingSeparation.collectAsVisibleState()
    val powerLimit by viewModel.hardware.chargePowerLimit.collectAsVisibleState()
    val airplane by viewModel.hardware.airplaneMode.collectAsVisibleState()
    val selectedIndex by viewModel.selectedDockIndex.collectAsVisibleState()
    val focusZone by viewModel.focusZone.collectAsVisibleState()
    BottomDockBar(
        performanceMode = performance, fanMode = fan, joystickLightEnabled = lights,
        chargingSeparation = separation, chargePowerLimit = powerLimit, airplaneMode = airplane,
        selectedDockIndex = selectedIndex, focusZone = focusZone,
        onItemClick = viewModel::onDockItemClick, modifier = modifier.fillMaxWidth()
    )
}
