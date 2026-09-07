package com.odin.desktop.ui.viewmodel

import com.odin.desktop.data.model.displayName
import com.odin.desktop.R
import com.odin.desktop.locale.AppLanguage
import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.odin.desktop.dashboard.DashboardAction
import com.odin.desktop.dashboard.DashboardRepository
import com.odin.desktop.dashboard.DashboardState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import com.odin.desktop.data.entity.TabEntity
import com.odin.desktop.data.model.InstalledApp
import com.odin.desktop.data.model.orderAllApps
import com.odin.desktop.data.model.AppSortMode
import com.odin.desktop.data.model.HOME_APP_LIMIT
import com.odin.desktop.data.model.homeAppCount
import com.odin.desktop.data.model.sortApps
import com.odin.desktop.data.model.moveApp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.odin.desktop.service.fan.HardwareController
import com.odin.desktop.ui.navigation.FocusZone
import com.odin.desktop.ui.components.AppActionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val appRepository = (application as com.odin.desktop.OdinDesktopApplication).appRepository

    val hardware = LauncherHardwareControls(context, viewModelScope)

    // --- 焦点与区域状态 ---
    private val _focusZone = MutableStateFlow(FocusZone.DASHBOARD)
    val focusZone: StateFlow<FocusZone> = _focusZone.asStateFlow()

    // --- Tab 状态 ---
    private val _tabs = MutableStateFlow<List<TabEntity>>(emptyList())
    val tabs: StateFlow<List<TabEntity>> = _tabs.asStateFlow()

    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    private val _isConfigFocusedInTabs = MutableStateFlow(false)
    val isConfigFocusedInTabs: StateFlow<Boolean> = _isConfigFocusedInTabs.asStateFlow()

    // Dashboard is a fixed page, independent of editable database tabs.
    private val _isDashboardSelected = MutableStateFlow(true)
    val isDashboardSelected = _isDashboardSelected.asStateFlow()
    private val _selectedDashboardControl = MutableStateFlow(0)
    val selectedDashboardControl = _selectedDashboardControl.asStateFlow()
    private val _dashboardState = MutableStateFlow(DashboardState())
    val dashboardState = _dashboardState.asStateFlow()
    private val dashboardActionChannel = Channel<DashboardAction>(Channel.BUFFERED)
    val dashboardActions = dashboardActionChannel.receiveAsFlow()
    private val dashboardRepository by lazy { DashboardRepository(context) }
    private var dashboardJob: Job? = null
    private var launcherVisible = false
    private val _telemetry = MutableStateFlow(LauncherTelemetry())
    val telemetry = _telemetry.asStateFlow()
    private var telemetryJob: Job? = null

    fun setLauncherVisible(visible: Boolean) {
        launcherVisible = visible
        if (!visible) {
            telemetryJob?.cancel()
            telemetryJob = null
            _telemetry.value = LauncherTelemetry()
        } else if (telemetryJob?.isActive != true) {
            telemetryJob = viewModelScope.launch {
                LauncherTelemetryRepository(context).observe().collect { _telemetry.value = it }
            }
        }
        updateDashboardCollection()
    }

    private fun updateDashboardCollection() {
        if (!launcherVisible || !_isDashboardSelected.value) {
            dashboardJob?.cancel()
            dashboardJob = null
        } else if (dashboardJob?.isActive != true) {
            dashboardJob = viewModelScope.launch {
                dashboardRepository.observe().collect { _dashboardState.value = it }
            }
        }
    }

    private fun contentFocus() = if (_isDashboardSelected.value) FocusZone.DASHBOARD else FocusZone.APPS

    fun selectDashboard() {
        if (navigationBlocked()) return
        _isDashboardSelected.value = true
        _isConfigFocusedInTabs.value = false
        _focusZone.value = FocusZone.DASHBOARD
        updateDashboardCollection()
    }

    fun onDashboardAction(action: DashboardAction) {
        if (!_isDashboardSelected.value || navigationBlocked()) return
        _selectedDashboardControl.value = action.ordinal
        _focusZone.value = FocusZone.DASHBOARD
        dashboardActionChannel.trySend(action)
    }

    private fun navigationBlocked() = _isConfigOpen.value || _isAppActionDialogOpen.value ||
        _isAppBatchManageDialogOpen.value || _isReorderingApps.value || _isAllAppsOpen.value || _isSortMenuOpen.value

    // --- 应用列表状态 ---
    private val _allInstalledApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val allInstalledApps: StateFlow<List<InstalledApp>> = _allInstalledApps.asStateFlow()

    private val _currentTabApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val currentTabApps: StateFlow<List<InstalledApp>> = _currentTabApps.asStateFlow()

    private val _selectedAppIndex = MutableStateFlow(0)
    val selectedAppIndex: StateFlow<Int> = _selectedAppIndex.asStateFlow()

    // --- 底部 Dock 状态 ---
    private val _selectedDockIndex = MutableStateFlow(0)
    val selectedDockIndex: StateFlow<Int> = _selectedDockIndex.asStateFlow()

    // --- Config 弹窗手柄导航状态 ---
    private val _isConfigOpen = MutableStateFlow(false)
    val isConfigOpen: StateFlow<Boolean> = _isConfigOpen.asStateFlow()

    private val _configSectionIndex = MutableStateFlow(0) // 0..5 左侧栏
    val configSectionIndex: StateFlow<Int> = _configSectionIndex.asStateFlow()

    private val _configInSubMenu = MutableStateFlow(false) // 是否进入右侧内容区
    val configInSubMenu: StateFlow<Boolean> = _configInSubMenu.asStateFlow()

    private val _configContentFocusIndex = MutableStateFlow(0) // 右侧内容项焦点
    val configContentFocusIndex: StateFlow<Int> = _configContentFocusIndex.asStateFlow()

    private val _appLanguage = MutableStateFlow(AppLanguage.current())
    val appLanguage = _appLanguage.asStateFlow()

    fun refreshAppLanguage() {
        _appLanguage.value = AppLanguage.current()
    }

    fun setAppLanguage(language: AppLanguage) {
        _configContentFocusIndex.value = language.ordinal
        _appLanguage.value = language
        language.apply()
    }

    private val _configTabActionIndex = MutableStateFlow(0) // Tab 编辑右侧行内按钮焦点
    val configTabActionIndex: StateFlow<Int> = _configTabActionIndex.asStateFlow()

    private val _isAppActionDialogOpen = MutableStateFlow(false)
    val isAppActionDialogOpen: StateFlow<Boolean> = _isAppActionDialogOpen.asStateFlow()

    private val _appUnderAction = MutableStateFlow<InstalledApp?>(null)
    val appUnderAction: StateFlow<InstalledApp?> = _appUnderAction.asStateFlow()

    private val _appActionFocusIndex = MutableStateFlow(0)
    val appActionFocusIndex: StateFlow<Int> = _appActionFocusIndex.asStateFlow()

    private val _appActionInTabPicker = MutableStateFlow(false)
    val appActionInTabPicker: StateFlow<Boolean> = _appActionInTabPicker.asStateFlow()

    private val _appActionTabPickerFocusIndex = MutableStateFlow(0)
    val appActionTabPickerFocusIndex: StateFlow<Int> = _appActionTabPickerFocusIndex.asStateFlow()

    private val _isAppBatchManageDialogOpen = MutableStateFlow(false)
    val isAppBatchManageDialogOpen: StateFlow<Boolean> = _isAppBatchManageDialogOpen.asStateFlow()

    private val _batchManageFocusIndex = MutableStateFlow(0)
    val batchManageFocusIndex: StateFlow<Int> = _batchManageFocusIndex.asStateFlow()

    private val _batchManageSearchQuery = MutableStateFlow("")
    val batchManageSearchQuery: StateFlow<String> = _batchManageSearchQuery.asStateFlow()

    private val _currentTabAppPackages = MutableStateFlow<Set<String>>(emptySet())
    val currentTabAppPackages: StateFlow<Set<String>> = _currentTabAppPackages.asStateFlow()

    // --- 图标排序编辑模式 (Y 键抖动模式) ---
    private val _isReorderingApps = MutableStateFlow(false)
    val isReorderingApps: StateFlow<Boolean> = _isReorderingApps.asStateFlow()

    private val _pickedAppIndex = MutableStateFlow<Int?>(null)
    val pickedAppIndex: StateFlow<Int?> = _pickedAppIndex.asStateFlow()

    private val _isAllAppsOpen = MutableStateFlow(false)
    val isAllAppsOpen = _isAllAppsOpen.asStateFlow()
    private var libraryReturnIndex = 0
    private var gridColumns = 1
    private val _sortMode = MutableStateFlow(AppSortMode.MANUAL)
    val sortMode = _sortMode.asStateFlow()
    private val _usageStatsAvailable = MutableStateFlow(false)
    val usageStatsAvailable = _usageStatsAvailable.asStateFlow()
    private val _isSortMenuOpen = MutableStateFlow(false)
    val isSortMenuOpen = _isSortMenuOpen.asStateFlow()
    private val _sortMenuIndex = MutableStateFlow(0)
    val sortMenuIndex = _sortMenuIndex.asStateFlow()
    private val orderSaveMutex = Mutex()
    private val pendingOrders = mutableMapOf<Long, List<String>>()
    private var scanJob: Job? = null

    private fun activeAppTab(): TabEntity? = if (_isAllAppsOpen.value)
        _tabs.value.firstOrNull { it.kind == com.odin.desktop.data.entity.TabKind.ALL_APPS }
        else _tabs.value.getOrNull(_selectedTabIndex.value)

    fun setGridColumns(columns: Int) { gridColumns = columns.coerceAtLeast(1) }

    private fun visibleAppCount() = homeAppCount(_currentTabApps.value.size,
        _isAllAppsOpen.value || _isReorderingApps.value)

    fun openAllApps() {
        if (navigationBlocked()) return
        libraryReturnIndex = HOME_APP_LIMIT.coerceAtMost(_currentTabApps.value.lastIndex.coerceAtLeast(0))
        _isAllAppsOpen.value = true
        _selectedAppIndex.value = 0
        _focusZone.value = FocusZone.APPS
        filterAppsForCurrentTab()
    }

    fun closeAllApps() {
        if (_isReorderingApps.value) exitReorderMode()
        _isAllAppsOpen.value = false
        _selectedAppIndex.value = libraryReturnIndex
        _focusZone.value = FocusZone.APPS
        filterAppsForCurrentTab()
    }

    fun openSortMenu() {
        if (_isDashboardSelected.value || _isConfigOpen.value || _isAppActionDialogOpen.value ||
            _isAppBatchManageDialogOpen.value || _isReorderingApps.value) return
        _sortMenuIndex.value = _sortMode.value.ordinal
        _isSortMenuOpen.value = true
    }

    fun closeSortMenu() { _isSortMenuOpen.value = false }

    fun setSortMode(mode: AppSortMode) {
        if (mode == AppSortMode.LAST_USED && !_usageStatsAvailable.value) return
        val tab = activeAppTab() ?: return
        _sortMode.value = mode
        appRepository.setSortMode(tab.id, mode)
        _selectedAppIndex.value = 0
        closeSortMenu()
        filterAppsForCurrentTab()
    }

    fun openUsageAccessSettings() {
        closeSortMenu()
        runCatching {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = android.net.Uri.parse("package:" + context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }.onFailure {
            Toast.makeText(context, context.getString(R.string.app_usage_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            appRepository.sanitizeDefaultTabs()
        }
        hardware.loadHardwareStates()
        observeTabs()
        scanInstalledApps()
    }

    override fun onCleared() {
        hardware.close()
        super.onCleared()
    }

    private var isFirstTabLoad = true

    private fun observeTabs() {
        viewModelScope.launch {
            appRepository.allTabs.collectLatest { tabList ->
                _tabs.value = tabList
                if (isFirstTabLoad && tabList.isNotEmpty()) {
                    isFirstTabLoad = false
                    val defaultIndex = tabList.indexOfFirst { it.isDefault }
                    _selectedTabIndex.value = if (defaultIndex >= 0) defaultIndex else 0
                } else if (_selectedTabIndex.value >= tabList.size && tabList.isNotEmpty()) {
                    _selectedTabIndex.value = 0
                }
                filterAppsForCurrentTab()
            }
        }
    }

    fun scanInstalledApps() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val apps = appRepository.getInstalledLaunchableApps()
            val currentInstalledPackages = apps.map { it.packageName }.toSet()

            // 清理已卸载应用的残留分类映射
            val currentTabPkgs = _currentTabAppPackages.value
            for (pkg in currentTabPkgs) {
                if (!currentInstalledPackages.contains(pkg)) {
                    appRepository.removeAppFromAllTabs(pkg)
                }
            }

            _usageStatsAvailable.value = appRepository.usageStatsAvailable
            _allInstalledApps.value = apps
            filterAppsForCurrentTab()
        }
    }

    private var filterJob: kotlinx.coroutines.Job? = null

    private fun filterAppsForCurrentTab() {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            val currentTab = activeAppTab()
            val allApps = _allInstalledApps.value
            if (currentTab == null) {
                _currentTabApps.value = allApps
                _currentTabAppPackages.value = allApps.map { it.packageName }.toSet()
                return@launch
            }
            _sortMode.value = appRepository.getSortMode(currentTab.id)
            appRepository.getAppsForTabFlow(currentTab.id).collectLatest { mappings ->
                if (_isReorderingApps.value) return@collectLatest
                val allTab = currentTab.kind == com.odin.desktop.data.entity.TabKind.ALL_APPS
                val members = if (allTab) allApps.map { it.packageName }.toSet()
                    else mappings.map { it.packageName }.toSet()
                val saved = pendingOrders[currentTab.id] ?: mappings.map { it.packageName }
                val manual = if (allTab) orderAllApps(allApps, saved) else {
                    val appMap = allApps.associateBy { it.packageName }
                    (saved + mappings.map { it.packageName }).distinct().filter { it in members }
                        .mapNotNull(appMap::get)
                }
                _currentTabAppPackages.value = members
                _currentTabApps.value = sortApps(manual, _sortMode.value,
                    context.resources.configuration.locales[0])
                _selectedAppIndex.value = _selectedAppIndex.value.coerceIn(0, (visibleAppCount() - 1).coerceAtLeast(0))
            }
        }
    }

    // --- 肩键 Tab 切换 (L1 / R1) ---
    fun onPrevTab() {
        if (_isConfigOpen.value && _configInSubMenu.value && _configSectionIndex.value == 3) {
            val currentTabs = _tabs.value
            val tab = currentTabs.getOrNull(_configContentFocusIndex.value)
            if (tab != null && _configContentFocusIndex.value > 0) {
                moveTabUp(tab)
                _configContentFocusIndex.value -= 1
            }
            return
        }
        if (navigationBlocked()) return
        val current = if (_isDashboardSelected.value) 0 else _selectedTabIndex.value + 1
        val previous = if (current > 0) current - 1 else _tabs.value.size
        if (previous == 0) selectDashboard() else selectTab(previous - 1)
    }

    fun onNextTab() {
        if (_isConfigOpen.value && _configInSubMenu.value && _configSectionIndex.value == 3) {
            val currentTabs = _tabs.value
            val tab = currentTabs.getOrNull(_configContentFocusIndex.value)
            if (tab != null && _configContentFocusIndex.value < currentTabs.size - 1) {
                moveTabDown(tab)
                _configContentFocusIndex.value += 1
            }
            return
        }
        if (navigationBlocked()) return
        val current = if (_isDashboardSelected.value) 0 else _selectedTabIndex.value + 1
        val next = (current + 1) % (_tabs.value.size + 1)
        if (next == 0) selectDashboard() else selectTab(next - 1)
    }

    fun selectTab(index: Int) {
        if (navigationBlocked()) return
        if (index in _tabs.value.indices) {
            _isDashboardSelected.value = false
            updateDashboardCollection()
            _selectedTabIndex.value = index
            _isConfigFocusedInTabs.value = false
            _selectedAppIndex.value = 0
            _focusZone.value = FocusZone.APPS
            filterAppsForCurrentTab()
        }
    }

    // --- 方向导航 (D-Pad / 摇杆) ---
    fun onNavigateLeft() {
        if (_isSortMenuOpen.value) {
            _sortMenuIndex.value = (_sortMenuIndex.value - 1).coerceIn(0, AppSortMode.entries.lastIndex)
            return
        }
        when (_focusZone.value) {
            FocusZone.TABS -> {
                if (_isConfigFocusedInTabs.value) {
                    if (_tabs.value.isEmpty()) selectDashboard() else selectTab(_tabs.value.lastIndex)
                } else if (!_isDashboardSelected.value) {
                    if (_selectedTabIndex.value > 0) selectTab(_selectedTabIndex.value - 1) else selectDashboard()
                }
                _focusZone.value = FocusZone.TABS
            }
            FocusZone.DASHBOARD -> {
                val index = _selectedDashboardControl.value
                _selectedDashboardControl.value = (index - 1).coerceAtLeast(0)
            }
            FocusZone.APPS -> {
                if (_isReorderingApps.value) {
                    if (_pickedAppIndex.value != null) {
                        movePickedAppLeft()
                    } else if (_selectedAppIndex.value > 0) {
                        _selectedAppIndex.value -= 1
                    }
                } else if (_selectedAppIndex.value > 0) {
                    _selectedAppIndex.value -= 1
                }
            }
            FocusZone.DOCK -> {
                if (_selectedDockIndex.value > 0) {
                    _selectedDockIndex.value -= 1
                }
            }
            FocusZone.CONFIG_MODAL -> {
                if (_configInSubMenu.value) {
                    when (_configSectionIndex.value) {
                        0 -> { // 摇杆灯颜色
                            if (_configContentFocusIndex.value > 0) {
                                _configContentFocusIndex.value -= 1
                            } else {
                                _configInSubMenu.value = false
                            }
                        }
                        3 -> { // Tab 编辑
                            if (_configTabActionIndex.value > 0) {
                                _configTabActionIndex.value -= 1
                            } else {
                                _configInSubMenu.value = false
                            }
                        }
                        else -> {
                            // 屏幕方向、默认桌面与自启、关于等单项/展示页面直接返回左侧菜单
                            _configInSubMenu.value = false
                        }
                    }
                }
            }
            FocusZone.APP_ACTION_MODAL -> {}
            FocusZone.APP_BATCH_MANAGE_MODAL -> {}
        }
    }

    fun onNavigateRight() {
        if (_isSortMenuOpen.value) {
            _sortMenuIndex.value = (_sortMenuIndex.value + 1).coerceIn(0, AppSortMode.entries.lastIndex)
            return
        }
        when (_focusZone.value) {
            FocusZone.TABS -> {
                if (!_isConfigFocusedInTabs.value) {
                    if (_isDashboardSelected.value && _tabs.value.isNotEmpty()) selectTab(0)
                    else if (!_isDashboardSelected.value && _selectedTabIndex.value < _tabs.value.lastIndex) selectTab(_selectedTabIndex.value + 1)
                    else _isConfigFocusedInTabs.value = true
                }
                _focusZone.value = FocusZone.TABS
            }
            FocusZone.DASHBOARD -> {
                val index = _selectedDashboardControl.value
                _selectedDashboardControl.value = (index + 1).coerceAtMost(DashboardAction.entries.lastIndex)
            }
            FocusZone.APPS -> {
                if (_isReorderingApps.value) {
                    if (_pickedAppIndex.value != null) {
                        movePickedAppRight()
                    } else if (_selectedAppIndex.value < visibleAppCount() - 1) {
                        _selectedAppIndex.value += 1
                    }
                } else if (_selectedAppIndex.value < visibleAppCount() - 1) {
                    _selectedAppIndex.value += 1
                }
            }
            FocusZone.DOCK -> {
                if (_selectedDockIndex.value < 4) {
                    _selectedDockIndex.value += 1
                }
            }
            FocusZone.CONFIG_MODAL -> {
                if (!_configInSubMenu.value) {
                    _configInSubMenu.value = true
                    _configContentFocusIndex.value = 0
                    _configTabActionIndex.value = 0
                } else when (_configSectionIndex.value) {
                    0 -> { // 摇杆灯预设色彩向右切换
                        if (_configContentFocusIndex.value < 5) {
                            _configContentFocusIndex.value += 1
                        }
                    }
                    3 -> { // Tab 编辑行内按钮向右切换
                        val currentTabs = _tabs.value
                        val tab = currentTabs.getOrNull(_configContentFocusIndex.value)
                        if (tab != null) {
                            val actions = com.odin.desktop.data.entity.getAvailableTabActions(tab, _configContentFocusIndex.value, currentTabs.size)
                            if (_configTabActionIndex.value < actions.size - 1) {
                                _configTabActionIndex.value += 1
                            }
                        }
                    }
                    // 屏幕方向、默认桌面、关于等右键不执行越界操作
                }
            }
            FocusZone.APP_ACTION_MODAL -> {}
            FocusZone.APP_BATCH_MANAGE_MODAL -> {}
        }
    }

    fun onNavigateUp() {
        if (_isSortMenuOpen.value) {
            _sortMenuIndex.value = (_sortMenuIndex.value - 1).coerceIn(0, AppSortMode.entries.lastIndex)
            return
        }
        when (_focusZone.value) {
            FocusZone.DOCK -> _focusZone.value = contentFocus()
            FocusZone.DASHBOARD -> _focusZone.value = FocusZone.TABS
            FocusZone.APPS -> {
                if (_isAllAppsOpen.value) navigateGrid(-gridColumns)
                else if (!_isReorderingApps.value) _focusZone.value = FocusZone.TABS
            }
            FocusZone.TABS -> {}
            FocusZone.CONFIG_MODAL -> {
                if (!_configInSubMenu.value) {
                    if (_configSectionIndex.value > 0) {
                        _configSectionIndex.value -= 1
                    }
                } else when (_configSectionIndex.value) {
                    1 -> { // 屏幕方向
                        if (_configContentFocusIndex.value > 0) {
                            _configContentFocusIndex.value -= 1
                        }
                    }
                    2 -> { _configContentFocusIndex.value = 0 }
                    3 -> { // Tab 列表
                        if (_configContentFocusIndex.value > 0) {
                            _configContentFocusIndex.value -= 1
                            clampTabActionIndex()
                        }
                    }
                    4 -> { // Language options
                        _configContentFocusIndex.value = (_configContentFocusIndex.value - 1).coerceAtLeast(0)
                    }
                }
            }
            FocusZone.APP_ACTION_MODAL -> {
                if (_appActionInTabPicker.value) {
                    if (_appActionTabPickerFocusIndex.value > 0) {
                        _appActionTabPickerFocusIndex.value -= 1
                    }
                } else {
                    if (_appActionFocusIndex.value > 0) {
                        _appActionFocusIndex.value -= 1
                    }
                }
            }
            FocusZone.APP_BATCH_MANAGE_MODAL -> {
                if (_batchManageFocusIndex.value > -1) {
                    _batchManageFocusIndex.value -= 1
                }
            }
        }
    }

    fun onNavigateDown() {
        if (_isSortMenuOpen.value) {
            _sortMenuIndex.value = (_sortMenuIndex.value + 1).coerceIn(0, AppSortMode.entries.lastIndex)
            return
        }
        when (_focusZone.value) {
            FocusZone.TABS -> _focusZone.value = contentFocus()
            FocusZone.DASHBOARD -> _focusZone.value = FocusZone.DOCK
            FocusZone.APPS -> {
                // 在排序状态下，光标只能在图标区域中移动，禁止移动到 Dock
                if (_isAllAppsOpen.value) navigateGrid(gridColumns)
                else if (!_isReorderingApps.value) {
                    _focusZone.value = FocusZone.DOCK
                }
            }
            FocusZone.DOCK -> {}
            FocusZone.CONFIG_MODAL -> {
                if (!_configInSubMenu.value) {
                    if (_configSectionIndex.value < 5) {
                        _configSectionIndex.value += 1
                    }
                } else when (_configSectionIndex.value) {
                    1 -> { // 屏幕方向（共2项：固定横屏、传感器横屏）
                        if (_configContentFocusIndex.value < 1) {
                            _configContentFocusIndex.value += 1
                        }
                    }
                    2 -> { _configContentFocusIndex.value = 0 }
                    3 -> { // Tab 列表
                        if (_configContentFocusIndex.value < _tabs.value.size - 1) {
                            _configContentFocusIndex.value += 1
                            clampTabActionIndex()
                        }
                    }
                    4 -> { // Language options
                        _configContentFocusIndex.value = (_configContentFocusIndex.value + 1).coerceAtMost(AppLanguage.entries.lastIndex)
                    }
                    // 摇杆灯、关于等无多行下移
                }
            }
            FocusZone.APP_ACTION_MODAL -> {
                if (_appActionInTabPicker.value) {
                    val currentTab = activeAppTab()
                    val targetTabs = _tabs.value.filter { it.id != currentTab?.id && it.kind != com.odin.desktop.data.entity.TabKind.ALL_APPS }
                    if (_appActionTabPickerFocusIndex.value < targetTabs.size - 1) {
                        _appActionTabPickerFocusIndex.value += 1
                    }
                } else {
                    if (_appActionFocusIndex.value < AppActionType.entries.lastIndex) {
                        _appActionFocusIndex.value += 1
                    }
                }
            }
            FocusZone.APP_BATCH_MANAGE_MODAL -> {
                val count = getFilteredBatchApps().size
                if (_batchManageFocusIndex.value < count - 1) {
                    _batchManageFocusIndex.value += 1
                }
            }
        }
    }

    private fun clampTabActionIndex() {
        val currentTabs = _tabs.value
        val tab = currentTabs.getOrNull(_configContentFocusIndex.value)
        if (tab != null) {
            val count = com.odin.desktop.data.entity.getAvailableTabActions(tab, _configContentFocusIndex.value, currentTabs.size).size
            if (count > 0) {
                _configTabActionIndex.value = _configTabActionIndex.value.coerceIn(0, count - 1)
            } else {
                _configTabActionIndex.value = 0
            }
        }
    }

    // --- 实体 A 键 (确定 / 启动 / 切档) ---
    fun onConfirm() {
        if (_isSortMenuOpen.value) {
            val mode = AppSortMode.entries[_sortMenuIndex.value]
            if (mode == AppSortMode.LAST_USED && !_usageStatsAvailable.value) openUsageAccessSettings()
            else setSortMode(mode)
            return
        }
        when (_focusZone.value) {
            FocusZone.DASHBOARD -> onDashboardAction(DashboardAction.entries[_selectedDashboardControl.value])
            FocusZone.TABS -> {
                if (_isConfigFocusedInTabs.value) {
                    openConfigDialog()
                } else {
                    _focusZone.value = contentFocus()
                }
            }
            FocusZone.APPS -> {
                if (_isReorderingApps.value) {
                    togglePickApp()
                    return
                }
                if (!_isAllAppsOpen.value && _selectedAppIndex.value == HOME_APP_LIMIT && _currentTabApps.value.size > HOME_APP_LIMIT) {
                    openAllApps()
                    return
                }
                val app = _currentTabApps.value.getOrNull(_selectedAppIndex.value)
                app?.let { launchApp(it) }
            }
            FocusZone.DOCK -> {
                triggerDockAction(_selectedDockIndex.value)
            }
            FocusZone.CONFIG_MODAL -> {
                if (!_configInSubMenu.value) {
                    _configInSubMenu.value = true
                    _configContentFocusIndex.value = 0
                } else {
                    triggerConfigSubAction()
                }
            }
            FocusZone.APP_ACTION_MODAL -> {
                val app = _appUnderAction.value
                if (app != null) {
                    if (_appActionInTabPicker.value) {
                        val currentTab = activeAppTab()
                        val targetTabs = _tabs.value.filter { it.id != currentTab?.id && it.kind != com.odin.desktop.data.entity.TabKind.ALL_APPS }
                        val target = targetTabs.getOrNull(_appActionTabPickerFocusIndex.value)
                        if (target != null) {
                            moveAppToTab(app, target.id)
                            closeAppActionDialog()
                        }
                    } else {
                        AppActionType.entries.getOrNull(_appActionFocusIndex.value)?.let {
                            executeAppAction(it)
                        }
                    }
                }
            }
            FocusZone.APP_BATCH_MANAGE_MODAL -> {
                if (_batchManageFocusIndex.value >= 0) {
                    val filtered = getFilteredBatchApps()
                    val app = filtered.getOrNull(_batchManageFocusIndex.value)
                    if (app != null) {
                        toggleAppInCurrentTab(app)
                    }
                }
            }
        }
    }

    // --- 触摸或点击 App 项 ---
    fun onAppClick(app: InstalledApp, index: Int) {
        _focusZone.value = FocusZone.APPS
        if (_isSortMenuOpen.value) return
        if (_isReorderingApps.value) {
            val picked = _pickedAppIndex.value
            if (picked != null) {
                movePickedAppTo(index)
                finishAppDrag()
            } else {
                _selectedAppIndex.value = index
                togglePickApp()
            }
        } else {
            _selectedAppIndex.value = index
            launchApp(app)
        }
    }

    // --- 触摸或点击 Dock 项 ---
    fun onDockItemClick(index: Int) {
        if (navigationBlocked()) return
        _focusZone.value = FocusZone.DOCK
        _selectedDockIndex.value = index
        triggerDockAction(index)
    }

    // --- 实体 B 键 (返回 / 取消) ---
    fun onBack(): Boolean {
        if (_isSortMenuOpen.value) { closeSortMenu(); return true }
        if (_isReorderingApps.value) {
            if (_pickedAppIndex.value != null) {
                _pickedAppIndex.value = null
                saveCurrentTabAppOrder()
            } else {
                exitReorderMode()
            }
            return true
        }
        if (_isAppActionDialogOpen.value) {
            if (_appActionInTabPicker.value) {
                _appActionInTabPicker.value = false
            } else {
                closeAppActionDialog()
            }
            return true
        }
        if (_isAppBatchManageDialogOpen.value) {
            closeBatchManageDialog()
            return true
        }
        if (_isConfigOpen.value) {
            if (_configInSubMenu.value) {
                _configInSubMenu.value = false
                return true
            }
            _isConfigOpen.value = false
            _focusZone.value = contentFocus()
            return true
        }
        if (_isAllAppsOpen.value) { closeAllApps(); return true }
        if (_focusZone.value == FocusZone.DOCK || _focusZone.value == FocusZone.TABS) {
            _isConfigFocusedInTabs.value = false
            _focusZone.value = contentFocus()
            return true
        }
        if (_focusZone.value == FocusZone.APPS && _selectedAppIndex.value > 0) {
            _selectedAppIndex.value = 0
            return true
        }
        if (_isDashboardSelected.value) {
            _selectedDashboardControl.value = 0
            return true
        }
        return false
    }

    // --- 实体 X 键 (管理菜单) ---
    fun onOptions() {
        if (_focusZone.value == FocusZone.APPS) {
            openBatchManageDialog()
        } else if (_focusZone.value == FocusZone.CONFIG_MODAL && _configInSubMenu.value && _configSectionIndex.value == 3) {
            val tab = _tabs.value.getOrNull(_configContentFocusIndex.value)
            if (tab != null && !tab.isDefault && tab.kind != com.odin.desktop.data.entity.TabKind.ALL_APPS) {
                deleteTab(tab)
            }
        }
    }

    private fun launchApp(app: InstalledApp) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            Toast.makeText(context, context.getString(R.string.text_cannot_launch_value, app.label), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 触发底部 5 大硬件状态切换 (对应 A 键或屏幕触碰点击)
     */
    private fun triggerDockAction(index: Int) {
        when (index) {
            0 -> hardware.cyclePerformanceMode()
            1 -> hardware.cycleFanMode()
            2 -> hardware.toggleJoystickLight()
            3 -> hardware.toggleChargePowerLimit()
            4 -> hardware.toggleAirplaneMode()
        }
    }

    // --- Config 弹窗手柄动作 ---
    private fun triggerConfigSubAction() {
        when (_configSectionIndex.value) {
            0 -> { // 摇杆灯颜色
                val colors = listOf("#ff00e5ff", "#ff7c4dff", "#ffff5252", "#ff00e676", "#ffffffff", "#ff2e2e2e")
                val selected = colors.getOrNull(_configContentFocusIndex.value % colors.size) ?: "#ff00e5ff"
                hardware.setJoystickColor(selected)
            }
            1 -> { // 屏幕方向 (仅支持固定横屏与传感器自适应横屏)
                val orientations = listOf(
                    HardwareController.ORIENTATION_LANDSCAPE,
                    HardwareController.ORIENTATION_SENSOR_LANDSCAPE
                )
                val sel = orientations.getOrNull(_configContentFocusIndex.value % orientations.size)
                    ?: HardwareController.ORIENTATION_LANDSCAPE
                hardware.setOrientationMode(sel)
            }
            2 -> hardware.requestDefaultHome()
            3 -> { // Tab 页编辑：执行当前光标左右选中的操作按钮
                val currentTabs = _tabs.value
                val tab = currentTabs.getOrNull(_configContentFocusIndex.value)
                if (tab != null) {
                    val actions = com.odin.desktop.data.entity.getAvailableTabActions(tab, _configContentFocusIndex.value, currentTabs.size)
                    val action = actions.getOrNull(_configTabActionIndex.value)
                    when (action) {
                        com.odin.desktop.data.entity.TabAction.MOVE_UP -> {
                            moveTabUp(tab)
                            if (_configContentFocusIndex.value > 0) {
                                _configContentFocusIndex.value -= 1
                                clampTabActionIndex()
                            }
                        }
                        com.odin.desktop.data.entity.TabAction.MOVE_DOWN -> {
                            moveTabDown(tab)
                            if (_configContentFocusIndex.value < currentTabs.size - 1) {
                                _configContentFocusIndex.value += 1
                                clampTabActionIndex()
                            }
                        }
                        com.odin.desktop.data.entity.TabAction.SET_DEFAULT -> {
                            setDefaultHomeTab(tab)
                            clampTabActionIndex()
                        }
                        com.odin.desktop.data.entity.TabAction.DELETE -> {
                            deleteTab(tab)
                            clampTabActionIndex()
                        }
                        null -> {}
                    }
                }
            }
            4 -> AppLanguage.entries.getOrNull(_configContentFocusIndex.value)?.let(::setAppLanguage)
            else -> {}
        }
    }

    fun openConfigDialog() {
        if (navigationBlocked()) return
        refreshAppLanguage()
        hardware.refreshHomeStatus()
        _isConfigOpen.value = true
        _configInSubMenu.value = false
        _configSectionIndex.value = 0
        _configContentFocusIndex.value = 0
        _configTabActionIndex.value = 0
        _focusZone.value = FocusZone.CONFIG_MODAL
    }

    fun closeConfigDialog() {
        _isConfigOpen.value = false
        _configInSubMenu.value = false
        _focusZone.value = contentFocus()
    }

    fun setConfigSection(index: Int) {
        _configSectionIndex.value = index.coerceIn(0, 5)
        _configInSubMenu.value = false
        _configContentFocusIndex.value = 0
    }

    fun addTab(name: String, isGame: Boolean = false) {
        if (_tabs.value.size >= 10) {
            Toast.makeText(context, context.getString(R.string.text_you_can_create_up_to_10_tabs), Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            appRepository.createTab(name, isGame)
        }
    }

    fun renameTab(tab: TabEntity, newName: String) {
        viewModelScope.launch {
            appRepository.renameTab(tab.id, newName)
        }
    }

    fun deleteTab(tab: TabEntity) {
        if (tab.kind == com.odin.desktop.data.entity.TabKind.ALL_APPS || tab.isDefault) {
            Toast.makeText(context, context.getString(R.string.text_the_default_category_cannot_be_deleted), Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            appRepository.deleteTab(tab.id)
        }
    }

    fun moveTabUp(tab: TabEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            appRepository.moveTabUp(tab)
        }
    }

    fun moveTabDown(tab: TabEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            appRepository.moveTabDown(tab)
        }
    }

    fun setDefaultHomeTab(tab: TabEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            appRepository.setDefaultHomeTab(tab.id)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.text_set_value_as_the_home_tab, tab.displayName(context)), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun moveAppToTab(app: InstalledApp, targetTabId: Long) {
        val currentTab = activeAppTab()
        if (currentTab != null && currentTab.kind != com.odin.desktop.data.entity.TabKind.ALL_APPS) {
            _currentTabApps.value = _currentTabApps.value.filter { it.packageName != app.packageName }
            _currentTabAppPackages.value = _currentTabAppPackages.value - app.packageName
            if (_selectedAppIndex.value >= _currentTabApps.value.size) {
                _selectedAppIndex.value = (_currentTabApps.value.size - 1).coerceAtLeast(0)
            }
        }
        viewModelScope.launch {
            val sourceId = currentTab?.takeUnless { it.kind == com.odin.desktop.data.entity.TabKind.ALL_APPS }?.id
            if (!appRepository.moveAppToTab(sourceId, targetTabId, app.packageName)) {
                filterAppsForCurrentTab()
                closeAppActionDialog()
                return@launch
            }
            closeAppActionDialog()
            val targetName = _tabs.value.find { it.id == targetTabId }?.displayName(context) ?: context.getString(R.string.text_target_category)
            Toast.makeText(context, context.getString(R.string.text_added_value_to_the_start_of_value, app.label, targetName), Toast.LENGTH_SHORT).show()
        }
    }

    // Manual edits work on the complete category, including icons beyond the home limit.
    fun enterReorderMode() {
        if (_focusZone.value != FocusZone.APPS || _isSortMenuOpen.value || _currentTabApps.value.isEmpty()) return
        val tab = activeAppTab() ?: return
        _isReorderingApps.value = true
        _pickedAppIndex.value = null
        _selectedAppIndex.value = _selectedAppIndex.value.coerceAtMost(_currentTabApps.value.lastIndex)
        _sortMode.value = AppSortMode.MANUAL
        appRepository.setSortMode(tab.id, AppSortMode.MANUAL)
    }

    fun pickAppForDrag(packageName: String) {
        val index = _currentTabApps.value.indexOfFirst { it.packageName == packageName }
        if (index < 0) return
        _focusZone.value = FocusZone.APPS
        if (!_isReorderingApps.value) enterReorderMode()
        if (!_isReorderingApps.value) return
        _selectedAppIndex.value = index
        _pickedAppIndex.value = index
    }

    fun moveDraggedApp(packageName: String, targetPackage: String) {
        if (!_isReorderingApps.value) return
        val from = _currentTabApps.value.indexOfFirst { it.packageName == packageName }
        val to = _currentTabApps.value.indexOfFirst { it.packageName == targetPackage }
        if (from < 0 || to < 0) return
        _pickedAppIndex.value = from
        movePickedAppTo(to)
    }

    fun finishAppDrag() {
        _pickedAppIndex.value = null
        saveCurrentTabAppOrder()
    }

    fun exitReorderMode() {
        saveCurrentTabAppOrder()
        _isReorderingApps.value = false
        _pickedAppIndex.value = null
        _selectedAppIndex.value = _selectedAppIndex.value.coerceAtMost((visibleAppCount() - 1).coerceAtLeast(0))
    }

    fun togglePickApp() {
        if (_pickedAppIndex.value == null) {
            if (_selectedAppIndex.value in _currentTabApps.value.indices) _pickedAppIndex.value = _selectedAppIndex.value
        } else finishAppDrag()
    }

    private fun movePickedAppTo(target: Int) {
        val from = _pickedAppIndex.value ?: return
        if (target !in _currentTabApps.value.indices) return
        _currentTabApps.value = moveApp(_currentTabApps.value, from, target)
        _selectedAppIndex.value = target
        _pickedAppIndex.value = target
    }

    private fun movePickedAppLeft() = movePickedAppTo(_selectedAppIndex.value - 1)
    private fun movePickedAppRight() = movePickedAppTo(_selectedAppIndex.value + 1)

    private fun navigateGrid(delta: Int) {
        val target = (_selectedAppIndex.value + delta).coerceIn(0, _currentTabApps.value.lastIndex.coerceAtLeast(0))
        if (_pickedAppIndex.value != null) movePickedAppTo(target) else _selectedAppIndex.value = target
    }

    private fun saveCurrentTabAppOrder() {
        val tabId = activeAppTab()?.id ?: return
        val pkgs = _currentTabApps.value.map { it.packageName }
        pendingOrders[tabId] = pkgs
        viewModelScope.launch {
            try {
                orderSaveMutex.withLock { appRepository.updateAppOrder(tabId, pkgs) }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Toast.makeText(context, context.getString(R.string.app_order_save_failed), Toast.LENGTH_LONG).show()
            } finally {
                if (pendingOrders[tabId] === pkgs) pendingOrders.remove(tabId)
            }
            if (!_isReorderingApps.value && activeAppTab()?.id == tabId) filterAppsForCurrentTab()
        }
    }

    fun removeAppFromCurrentTab(app: InstalledApp) {
        val currentTab = activeAppTab() ?: return
        if (currentTab.kind == com.odin.desktop.data.entity.TabKind.ALL_APPS) {
            Toast.makeText(context, context.getString(R.string.text_all_apps_contains_every_installed_app_icons), Toast.LENGTH_SHORT).show()
            return
        }
        _currentTabApps.value = _currentTabApps.value.filter { it.packageName != app.packageName }
        _currentTabAppPackages.value = _currentTabAppPackages.value - app.packageName
        if (_selectedAppIndex.value >= _currentTabApps.value.size) {
            _selectedAppIndex.value = (_currentTabApps.value.size - 1).coerceAtLeast(0)
        }
        viewModelScope.launch {
            appRepository.removeAppFromTab(currentTab.id, app.packageName)
            closeAppActionDialog()
            Toast.makeText(context, context.getString(R.string.text_removed_icon_from_value, currentTab.displayName(context)), Toast.LENGTH_SHORT).show()
        }
    }

    // --- 应用操作模态框 (Y 键) ---
    fun openAppActionDialog() {
        if (_isDashboardSelected.value || _isConfigOpen.value || _isAppBatchManageDialogOpen.value || _isAppActionDialogOpen.value || _isSortMenuOpen.value) return
        if (_isReorderingApps.value) {
            exitReorderMode()
        }
        if (!_isAllAppsOpen.value && !_isReorderingApps.value && _selectedAppIndex.value >= HOME_APP_LIMIT) return
        val app = _currentTabApps.value.getOrNull(_selectedAppIndex.value) ?: return
        _appUnderAction.value = app
        _appActionFocusIndex.value = 0
        _appActionInTabPicker.value = false
        _appActionTabPickerFocusIndex.value = 0
        _isAppActionDialogOpen.value = true
        _focusZone.value = FocusZone.APP_ACTION_MODAL
    }

    fun closeAppActionDialog() {
        _isAppActionDialogOpen.value = false
        _appActionInTabPicker.value = false
        _focusZone.value = FocusZone.APPS
    }

    fun setAppActionFocusIndex(index: Int) {
        _appActionFocusIndex.value = index.coerceIn(0, AppActionType.entries.lastIndex)
    }

    fun setAppActionTabPickerFocusIndex(index: Int) {
        _appActionTabPickerFocusIndex.value = index
    }

    fun executeAppAction(type: AppActionType) {
        val app = _appUnderAction.value ?: return
        when (type) {
            AppActionType.MOVE_TO_TAB -> {
                val currentTab = activeAppTab()
                val targetTabs = _tabs.value.filter { it.id != currentTab?.id && it.kind != com.odin.desktop.data.entity.TabKind.ALL_APPS }
                if (targetTabs.isNotEmpty()) {
                    _appActionInTabPicker.value = true
                    _appActionTabPickerFocusIndex.value = 0
                } else {
                    Toast.makeText(context, context.getString(R.string.text_no_other_custom_categories_are_available), Toast.LENGTH_SHORT).show()
                }
            }
            AppActionType.APP_DETAILS -> {
                openAppDetails(app)
                closeAppActionDialog()
            }
            AppActionType.REMOVE_ICON -> {
                removeAppFromCurrentTab(app)
            }
        }
    }

    fun openAppDetails(app: InstalledApp) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", app.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.text_cannot_open_app_details_value, e.message), Toast.LENGTH_SHORT).show()
        }
    }


    // --- 批量增删分类应用模态框 (X 键) ---
    fun openBatchManageDialog() {
        if (_isSortMenuOpen.value) return
        if (_isDashboardSelected.value || _isConfigOpen.value || _isAppBatchManageDialogOpen.value || _isAppActionDialogOpen.value || _isReorderingApps.value) return
        val currentTab = activeAppTab()
        if (currentTab != null && currentTab.kind == com.odin.desktop.data.entity.TabKind.ALL_APPS) {
            Toast.makeText(context, context.getString(R.string.text_all_apps_is_managed_automatically), Toast.LENGTH_SHORT).show()
            return
        }
        _batchManageFocusIndex.value = 0
        _batchManageSearchQuery.value = ""
        _isAppBatchManageDialogOpen.value = true
        _focusZone.value = FocusZone.APP_BATCH_MANAGE_MODAL
    }

    fun closeBatchManageDialog() {
        _isAppBatchManageDialogOpen.value = false
        _focusZone.value = FocusZone.APPS
    }

    fun setBatchManageSearchQuery(query: String) {
        _batchManageSearchQuery.value = query
        _batchManageFocusIndex.value = 0
    }

    fun getFilteredBatchApps(): List<InstalledApp> {
        val all = _allInstalledApps.value
        val q = _batchManageSearchQuery.value.trim().lowercase()
        return if (q.isBlank()) all else all.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    fun toggleAppInCurrentTab(app: InstalledApp) {
        val currentTab = activeAppTab() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (_currentTabAppPackages.value.contains(app.packageName)) {
                appRepository.removeAppFromTab(currentTab.id, app.packageName)
            } else {
                appRepository.addAppToTab(currentTab.id, app.packageName)
                withContext(Dispatchers.Main) {
                    _selectedAppIndex.value = 0
                }
            }
        }
    }
}
