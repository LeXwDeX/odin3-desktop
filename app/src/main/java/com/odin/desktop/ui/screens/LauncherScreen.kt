package com.odin.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.viewmodel.LauncherViewModel
import com.odin.desktop.ui.state.collectAsVisibleState
import com.odin.desktop.ui.background.ConsoleBackground

/** Screen assembly only. Business state, rendering and input each have separate owners. */
@Composable
fun LauncherScreen(viewModel: LauncherViewModel, onOrientationChange: (Int) -> Unit) {
    LauncherOrientationEffect(viewModel, onOrientationChange)
    val isAllAppsOpen by viewModel.isAllAppsOpen.collectAsVisibleState()
    Box(Modifier.fillMaxSize().background(LocalOdinPalette.current.background)) {
        LauncherBackdrop(viewModel)
        LauncherContentRoute(viewModel)
        if (!isAllAppsOpen) {
            LauncherHeaderRoute(viewModel, Modifier.align(Alignment.TopCenter))
            LauncherDockRoute(viewModel, Modifier.align(Alignment.BottomCenter))
        }
    }
    LauncherDialogRoutes(viewModel)
}

@Composable
private fun LauncherOrientationEffect(viewModel: LauncherViewModel, onOrientationChange: (Int) -> Unit) {
    val orientation by viewModel.hardware.orientationMode.collectAsVisibleState()
    androidx.compose.runtime.LaunchedEffect(orientation) {
        if (orientation >= 0) onOrientationChange(orientation)
    }
}

@Composable
private fun LauncherBackdrop(viewModel: LauncherViewModel) {
    val configOpen by viewModel.isConfigOpen.collectAsVisibleState()
    val actionOpen by viewModel.isAppActionDialogOpen.collectAsVisibleState()
    val manageOpen by viewModel.isAppBatchManageDialogOpen.collectAsVisibleState()
    val sortOpen by viewModel.isSortMenuOpen.collectAsVisibleState()
    val reordering by viewModel.isReorderingApps.collectAsVisibleState()
    ConsoleBackground(
        modifier = Modifier.fillMaxSize(),
        motionEnabled = !configOpen && !actionOpen && !manageOpen && !sortOpen && !reordering
    )
}
