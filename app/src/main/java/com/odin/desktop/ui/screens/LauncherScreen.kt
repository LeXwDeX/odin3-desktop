package com.odin.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odin.desktop.ui.components.AppActionDialog
import com.odin.desktop.ui.components.AppBatchManageDialog
import com.odin.desktop.ui.components.AppHorizontalRow
import com.odin.desktop.ui.components.BottomDockBar
import com.odin.desktop.ui.components.ConfigDialog
import com.odin.desktop.ui.components.TopTabBar
import com.odin.desktop.ui.navigation.FocusZone
import com.odin.desktop.ui.theme.CyanAccent
import com.odin.desktop.ui.theme.PureBlack
import com.odin.desktop.ui.theme.TextDim
import com.odin.desktop.ui.theme.TextWhite
import com.odin.desktop.ui.viewmodel.LauncherViewModel

@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel,
    onOrientationChange: (Int) -> Unit
) {
    val tabs by viewModel.tabs.collectAsState()
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsState()
    val isConfigFocused by viewModel.isConfigFocusedInTabs.collectAsState()
    val focusZone by viewModel.focusZone.collectAsState()

    val currentTabApps by viewModel.currentTabApps.collectAsState()
    val selectedAppIndex by viewModel.selectedAppIndex.collectAsState()

    val selectedDockIndex by viewModel.selectedDockIndex.collectAsState()
    val performanceMode by viewModel.performanceMode.collectAsState()
    val fanMode by viewModel.fanMode.collectAsState()
    val joystickLightEnabled by viewModel.joystickLightEnabled.collectAsState()
    val joystickColor by viewModel.joystickColor.collectAsState()
    val chargeLimit80 by viewModel.chargeLimit80.collectAsState()
    val airplaneMode by viewModel.airplaneMode.collectAsState()
    val orientationMode by viewModel.orientationMode.collectAsState()
    val autoFanControlEnabled by viewModel.autoFanControlEnabled.collectAsState()
    val currentSocTemp by viewModel.currentSocTemp.collectAsState()

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

    androidx.compose.runtime.LaunchedEffect(orientationMode) {
        onOrientationChange(orientationMode)
    }

    // 硬件温度与风扇保护周期轮询 (仅当弹窗打开时活跃，减少后台功耗)
    androidx.compose.runtime.LaunchedEffect(isConfigOpen) {
        while (isConfigOpen) {
            viewModel.refreshSocTemp()
            kotlinx.coroutines.delay(1500)
        }
    }

    // 掌机控制台级绝对屏幕居中与零像素抖动布局体系
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        // 1. 中部应用大卡片滑带 (严格屏幕级垂直绝对居中，居于屏幕中轴线上)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AppHorizontalRow(
                apps = currentTabApps,
                selectedAppIndex = selectedAppIndex,
                focusZone = focusZone,
                isReordering = isReorderingApps,
                pickedIndex = pickedAppIndex,
                onAppClick = { viewModel.onConfirm() }
            )
        }

        // 2. 首页 App Name 与包名详情 (严格固定独立绝对槽位，彻底杜绝相对推挤与字符抖动)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = 64.dp, start = 32.dp, end = 32.dp)
                .height(60.dp)
        ) {
            val hoveredApp = currentTabApps.getOrNull(selectedAppIndex)

            // App Name 槽位：绝对固定在 Top(0.dp)，固定高度 32.dp，严格顶部对齐，零像素位移
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(32.dp),
                contentAlignment = Alignment.TopStart
            ) {
                Text(
                    text = hoveredApp?.label ?: if (currentTabApps.isEmpty()) "该分类暂无应用" else "",
                    color = if (focusZone == FocusZone.APPS) CyanAccent else TextWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeight = 28.sp,
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Top,
                            trim = LineHeightStyle.Trim.Both
                        )
                    )
                )
            }

            // 包名槽位：绝对固定在 Top(34.dp)，固定高度 20.dp，严格顶部对齐，与 App Name 彻底物理隔离
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 34.dp)
                    .fillMaxWidth()
                    .height(20.dp),
                contentAlignment = Alignment.TopStart
            ) {
                Text(
                    text = hoveredApp?.packageName ?: "",
                    color = TextDim,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeight = 16.sp,
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Top,
                            trim = LineHeightStyle.Trim.Both
                        )
                    )
                )
            }
        }

        // 3. 顶部 Tab 栏 (顶部对齐)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            TopTabBar(
                tabs = tabs,
                selectedTabIndex = selectedTabIndex,
                isConfigFocused = isConfigFocused,
                focusZone = focusZone,
                onTabSelected = { index ->
                    viewModel.onPrevTab()
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
                chargeLimit80 = chargeLimit80,
                airplaneMode = airplaneMode,
                selectedDockIndex = selectedDockIndex,
                focusZone = focusZone,
                onItemClick = { index ->
                    viewModel.onDockItemClick(index)
                }
            )
        }
    }

    // 5. Config 设置弹窗 (支持手柄 D-Pad 上下左右与 A/B/X/L1/R1 盲操)
    ConfigDialog(
        isOpen = isConfigOpen,
        onDismiss = { viewModel.closeConfigDialog() },
        selectedSection = configSectionIndex,
        inSubMenu = configInSubMenu,
        subFocusIndex = configContentFocusIndex,
        onSectionClick = { index -> viewModel.setConfigSection(index) },
        currentJoystickColor = joystickColor,
        currentOrientation = orientationMode,
        autoFanControlEnabled = autoFanControlEnabled,
        socTemp = currentSocTemp,
        onColorSelect = { hex -> viewModel.setJoystickColor(hex) },
        onOrientationSelect = { mode -> viewModel.setOrientationMode(mode) },
        onToggleAutoFan = { viewModel.toggleAutoFanControl() },
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
        currentTab = tabs.getOrNull(selectedTabIndex),
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
        currentTab = tabs.getOrNull(selectedTabIndex),
        allApps = allInstalledApps,
        currentTabAppPackages = currentTabAppPackages,
        searchQuery = batchManageSearchQuery,
        focusIndex = batchManageFocusIndex,
        onSearchChange = { query -> viewModel.setBatchManageSearchQuery(query) },
        onDismiss = { viewModel.closeBatchManageDialog() },
        onToggleApp = { app -> viewModel.toggleAppInCurrentTab(app) }
    )
}
