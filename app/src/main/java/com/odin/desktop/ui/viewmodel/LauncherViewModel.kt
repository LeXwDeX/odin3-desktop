package com.odin.desktop.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.odin.desktop.dashboard.DashboardAction
import com.odin.desktop.dashboard.DashboardRepository
import com.odin.desktop.dashboard.DashboardState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import com.odin.desktop.data.entity.TabEntity
import com.odin.desktop.data.model.InstalledApp
import com.odin.desktop.data.repository.AppRepository
import com.odin.desktop.service.fan.HardwareController
import com.odin.desktop.ui.navigation.FocusZone
import com.odin.desktop.ui.components.AppActionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val appRepository = AppRepository(
        context,
        (application as com.odin.desktop.OdinDesktopApplication).database.tabDao(),
        application.database.appMappingDao()
    )

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

    fun setLauncherVisible(visible: Boolean) {
        launcherVisible = visible
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
        _isAppBatchManageDialogOpen.value || _isReorderingApps.value

    // --- 应用列表状态 ---
    private val _allInstalledApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val allInstalledApps: StateFlow<List<InstalledApp>> = _allInstalledApps.asStateFlow()

    private val _currentTabApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val currentTabApps: StateFlow<List<InstalledApp>> = _currentTabApps.asStateFlow()

    private val _selectedAppIndex = MutableStateFlow(0)
    val selectedAppIndex: StateFlow<Int> = _selectedAppIndex.asStateFlow()

    val hoveredApp: StateFlow<InstalledApp?> get() {
        val apps = _currentTabApps.value
        val index = _selectedAppIndex.value
        return MutableStateFlow(apps.getOrNull(index)).asStateFlow()
    }

    // --- 底部 Dock 状态 ---
    private val _selectedDockIndex = MutableStateFlow(0)
    val selectedDockIndex: StateFlow<Int> = _selectedDockIndex.asStateFlow()

    private val _performanceMode = MutableStateFlow(-1)
    val performanceMode: StateFlow<Int> = _performanceMode.asStateFlow()

    private val _fanMode = MutableStateFlow(-1)
    val fanMode: StateFlow<Int> = _fanMode.asStateFlow()

    private val _joystickLightEnabled = MutableStateFlow(false)
    val joystickLightEnabled: StateFlow<Boolean> = _joystickLightEnabled.asStateFlow()

    private val _joystickColor = MutableStateFlow("#ff00e5ff")
    val joystickColor: StateFlow<String> = _joystickColor.asStateFlow()

    private val _chargingSeparation = MutableStateFlow(false)
    val chargingSeparation: StateFlow<Boolean> = _chargingSeparation.asStateFlow()

    private val _chargePowerLimit = MutableStateFlow(true)
    val chargePowerLimit: StateFlow<Boolean> = _chargePowerLimit.asStateFlow()

    private val _chargeLimit80 = MutableStateFlow(false)
    val chargeLimit80: StateFlow<Boolean> = _chargeLimit80.asStateFlow()
    private val hardwareLock = Mutex()
    private var perfJob: Job? = null
    private var fanJob: Job? = null
    private var lightJob: Job? = null
    private var chargePowerJob: Job? = null
    private var chargeSeparationJob: Job? = null
    private var airplaneJob: Job? = null

    private val _airplaneMode = MutableStateFlow(false)
    val airplaneMode: StateFlow<Boolean> = _airplaneMode.asStateFlow()

    private val _orientationMode = MutableStateFlow(HardwareController.ORIENTATION_LANDSCAPE)
    val orientationMode: StateFlow<Int> = _orientationMode.asStateFlow()

    private val _autoFanControlEnabled = MutableStateFlow(true)
    val autoFanControlEnabled: StateFlow<Boolean> = _autoFanControlEnabled.asStateFlow()

    private val _currentSocTemp = MutableStateFlow(Float.NaN)
    val currentSocTemp: StateFlow<Float> = _currentSocTemp.asStateFlow()

    private val _isDefaultHome = MutableStateFlow(false)
    val isDefaultHome: StateFlow<Boolean> = _isDefaultHome.asStateFlow()

    private val _bootAutoStartEnabled = MutableStateFlow(true)
    val bootAutoStartEnabled: StateFlow<Boolean> = _bootAutoStartEnabled.asStateFlow()

    private val _requestRoleEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestRoleEvent: SharedFlow<Unit> = _requestRoleEvent.asSharedFlow()

    // --- Config 弹窗手柄导航状态 ---
    private val _isConfigOpen = MutableStateFlow(false)
    val isConfigOpen: StateFlow<Boolean> = _isConfigOpen.asStateFlow()

    private val _configSectionIndex = MutableStateFlow(0) // 0..5 左侧栏
    val configSectionIndex: StateFlow<Int> = _configSectionIndex.asStateFlow()

    private val _configInSubMenu = MutableStateFlow(false) // 是否进入右侧内容区
    val configInSubMenu: StateFlow<Boolean> = _configInSubMenu.asStateFlow()

    private val _configContentFocusIndex = MutableStateFlow(0) // 右侧内容项焦点
    val configContentFocusIndex: StateFlow<Int> = _configContentFocusIndex.asStateFlow()

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

    init {
        viewModelScope.launch(Dispatchers.IO) {
            appRepository.sanitizeDefaultTabs()
        }
        loadHardwareStates()
        observeTabs()
        scanInstalledApps()
    }

    fun loadHardwareStates() {
        viewModelScope.launch(Dispatchers.IO) {
            hardwareLock.withLock { refreshHardwareStates(refreshPerformance = true) }
        }
    }

    private fun refreshHardwareStates(refreshPerformance: Boolean = false) {
        // A failed read keeps the last observed value; requested values never become device state.
        if (refreshPerformance || _performanceMode.value < 0) {
            runCatching { HardwareController.getPerformanceMode(context) }.onSuccess { _performanceMode.value = it }.onFailure { _performanceMode.value = -1 }
        }
        runCatching { HardwareController.getFanMode(context) }.onSuccess { _fanMode.value = it }
        runCatching { HardwareController.isJoystickLightEnabled(context) }.onSuccess { _joystickLightEnabled.value = it }
        runCatching { HardwareController.getJoystickColor(context) }.onSuccess { _joystickColor.value = it.substringBefore(',') }
        runCatching { HardwareController.isChargingSeparationEnabled(context) }.onSuccess { _chargingSeparation.value = it }
        runCatching { HardwareController.isChargePowerLimit5V(context) }.onSuccess { _chargePowerLimit.value = it }
        runCatching { HardwareController.isChargeLimit80Enabled(context) }.onSuccess { _chargeLimit80.value = it }
        runCatching { HardwareController.isAirplaneModeOn(context) }.onSuccess { _airplaneMode.value = it }
        _autoFanControlEnabled.value = HardwareController.isAutoFanControlEnabled(context)
        _orientationMode.value = HardwareController.getOrientationMode(context)
        _isDefaultHome.value = HardwareController.isDefaultHome(context)
        _bootAutoStartEnabled.value = HardwareController.isBootAutoStartEnabled(context)
        _currentSocTemp.value = runCatching { HardwareController.getMaxCpuGpuTemp() }.getOrDefault(Float.NaN)
    }

    private fun changeHardware(refreshPerformance: Boolean = false, action: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            hardwareLock.withLock {
                try {
                    action()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("OdinHardware", "Hardware action failed", failure)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, failure.message ?: "硬件设置未生效，请重试", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    refreshHardwareStates(refreshPerformance)
                }
            }
        }
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
        viewModelScope.launch {
            val apps = appRepository.getInstalledLaunchableApps()
            val currentInstalledPackages = apps.map { it.packageName }.toSet()

            // 清理已卸载应用的残留分类映射
            val currentTabPkgs = _currentTabAppPackages.value
            for (pkg in currentTabPkgs) {
                if (!currentInstalledPackages.contains(pkg)) {
                    appRepository.removeAppFromAllTabs(pkg)
                }
            }

            _allInstalledApps.value = apps
            filterAppsForCurrentTab()
        }
    }

    private var filterJob: kotlinx.coroutines.Job? = null

    private fun filterAppsForCurrentTab() {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            val currentTab = _tabs.value.getOrNull(_selectedTabIndex.value)
            val allApps = _allInstalledApps.value

            if (currentTab == null) {
                _currentTabApps.value = allApps
                _currentTabAppPackages.value = allApps.map { it.packageName }.toSet()
            } else if (currentTab.name == "全部应用") {
                val mappings = withContext(Dispatchers.IO) {
                    appRepository.getAppsForTabFlow(currentTab.id)
                }
                mappings.collectLatest { mappingList ->
                    if (_isReorderingApps.value) return@collectLatest
                    val appMap = allApps.associateBy { it.packageName }
                    val orderedApps = mappingList.mapNotNull { appMap[it.packageName] }
                    val orderedPkgSet = orderedApps.map { it.packageName }.toSet()
                    val remainingApps = allApps.filter { !orderedPkgSet.contains(it.packageName) }
                    _currentTabApps.value = orderedApps + remainingApps
                    _currentTabAppPackages.value = allApps.map { it.packageName }.toSet()
                    if (_selectedAppIndex.value >= _currentTabApps.value.size) {
                        _selectedAppIndex.value = 0
                    }
                }
            } else {
                val mappings = withContext(Dispatchers.IO) {
                    appRepository.getAppsForTabFlow(currentTab.id)
                }
                mappings.collectLatest { mappingList ->
                    if (_isReorderingApps.value) return@collectLatest
                    val pkgSet = mappingList.map { it.packageName }.toSet()
                    _currentTabAppPackages.value = pkgSet
                    val appMap = allApps.associateBy { it.packageName }
                    val orderedApps = mappingList.mapNotNull { appMap[it.packageName] }
                    val extraApps = allApps.filter { pkgSet.contains(it.packageName) && !orderedApps.contains(it) }
                    _currentTabApps.value = orderedApps + extraApps
                    if (_selectedAppIndex.value >= _currentTabApps.value.size) {
                        _selectedAppIndex.value = 0
                    }
                }
            }
        }
    }

    // --- 肩键 Tab 切换 (L1 / R1) ---
    fun onPrevTab() {
        if (_isConfigOpen.value && _configInSubMenu.value && _configSectionIndex.value == 4) {
            val currentTabs = _tabs.value
            val tab = currentTabs.getOrNull(_configContentFocusIndex.value)
            if (tab != null && _configContentFocusIndex.value > 0) {
                moveTabUp(tab)
                _configContentFocusIndex.value -= 1
            }
            return
        }
        if (_isConfigOpen.value || _isAppActionDialogOpen.value || _isAppBatchManageDialogOpen.value || _isReorderingApps.value) return
        val current = if (_isDashboardSelected.value) 0 else _selectedTabIndex.value + 1
        val previous = if (current > 0) current - 1 else _tabs.value.size
        if (previous == 0) selectDashboard() else selectTab(previous - 1)
    }

    fun onNextTab() {
        if (_isConfigOpen.value && _configInSubMenu.value && _configSectionIndex.value == 4) {
            val currentTabs = _tabs.value
            val tab = currentTabs.getOrNull(_configContentFocusIndex.value)
            if (tab != null && _configContentFocusIndex.value < currentTabs.size - 1) {
                moveTabDown(tab)
                _configContentFocusIndex.value += 1
            }
            return
        }
        if (_isConfigOpen.value || _isAppActionDialogOpen.value || _isAppBatchManageDialogOpen.value || _isReorderingApps.value) return
        val current = if (_isDashboardSelected.value) 0 else _selectedTabIndex.value + 1
        val next = (current + 1) % (_tabs.value.size + 1)
        if (next == 0) selectDashboard() else selectTab(next - 1)
    }

    fun selectTab(index: Int) {
        if (_isConfigOpen.value || _isAppActionDialogOpen.value || _isAppBatchManageDialogOpen.value || _isReorderingApps.value) return
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
                        4 -> { // Tab 编辑
                            if (_configTabActionIndex.value > 0) {
                                _configTabActionIndex.value -= 1
                            } else {
                                _configInSubMenu.value = false
                            }
                        }
                        else -> {
                            // 屏幕方向、默认桌面与自启、自动风扇控制、关于等单项/展示页面直接返回左侧菜单
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
                    } else if (_selectedAppIndex.value < _currentTabApps.value.size - 1) {
                        _selectedAppIndex.value += 1
                    }
                } else if (_selectedAppIndex.value < _currentTabApps.value.size - 1) {
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
                    4 -> { // Tab 编辑行内按钮向右切换
                        val currentTabs = _tabs.value
                        val tab = currentTabs.getOrNull(_configContentFocusIndex.value)
                        if (tab != null) {
                            val actions = com.odin.desktop.data.entity.getAvailableTabActions(tab, _configContentFocusIndex.value, currentTabs.size)
                            if (_configTabActionIndex.value < actions.size - 1) {
                                _configTabActionIndex.value += 1
                            }
                        }
                    }
                    // 屏幕方向、默认桌面、自动风扇控制、关于等右键不执行越界操作
                }
            }
            FocusZone.APP_ACTION_MODAL -> {}
            FocusZone.APP_BATCH_MANAGE_MODAL -> {}
        }
    }

    fun onNavigateUp() {
        when (_focusZone.value) {
            FocusZone.DOCK -> _focusZone.value = contentFocus()
            FocusZone.DASHBOARD -> _focusZone.value = FocusZone.TABS
            FocusZone.APPS -> {
                if (!_isReorderingApps.value) _focusZone.value = FocusZone.TABS
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
                    2 -> { // 默认桌面与自启
                        if (_configContentFocusIndex.value > 0) {
                            _configContentFocusIndex.value -= 1
                        }
                    }
                    4 -> { // Tab 列表
                        if (_configContentFocusIndex.value > 0) {
                            _configContentFocusIndex.value -= 1
                            clampTabActionIndex()
                        }
                    }
                    // 自动风扇控制等不可向上越界
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
        when (_focusZone.value) {
            FocusZone.TABS -> _focusZone.value = contentFocus()
            FocusZone.DASHBOARD -> _focusZone.value = FocusZone.DOCK
            FocusZone.APPS -> {
                // 在排序状态下，光标只能在图标区域中移动，禁止移动到 Dock
                if (!_isReorderingApps.value) {
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
                    2 -> { // 默认桌面与自启（共2项：默认主屏幕、开机自启）
                        if (_configContentFocusIndex.value < 1) {
                            _configContentFocusIndex.value += 1
                        }
                    }
                    3 -> {
                        // 自动风扇控制：仅可选中控制开关 (index 0)，下方为展示内容，不可被光标选中
                    }
                    4 -> { // Tab 列表
                        if (_configContentFocusIndex.value < _tabs.value.size - 1) {
                            _configContentFocusIndex.value += 1
                            clampTabActionIndex()
                        }
                    }
                    // 摇杆灯、关于等无多行下移
                }
            }
            FocusZone.APP_ACTION_MODAL -> {
                if (_appActionInTabPicker.value) {
                    val currentTab = _tabs.value.getOrNull(_selectedTabIndex.value)
                    val targetTabs = _tabs.value.filter { it.id != currentTab?.id && it.name != "全部应用" }
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
                        val currentTab = _tabs.value.getOrNull(_selectedTabIndex.value)
                        val targetTabs = _tabs.value.filter { it.id != currentTab?.id && it.name != "全部应用" }
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
        _selectedAppIndex.value = index
        if (_isReorderingApps.value) {
            togglePickApp()
        } else {
            launchApp(app)
        }
    }

    // --- 触摸或点击 Dock 项 ---
    fun onDockItemClick(index: Int) {
        _focusZone.value = FocusZone.DOCK
        _selectedDockIndex.value = index
        triggerDockAction(index)
    }

    // --- 实体 B 键 (返回 / 取消) ---
    fun onBack(): Boolean {
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
        } else if (_focusZone.value == FocusZone.CONFIG_MODAL && _configInSubMenu.value && _configSectionIndex.value == 4) {
            val tab = _tabs.value.getOrNull(_configContentFocusIndex.value)
            if (tab != null && !tab.isDefault && tab.name != "全部应用") {
                deleteTab(tab)
            }
        }
    }

    private fun launchApp(app: InstalledApp) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            com.odin.desktop.shader.engine.VideoShaderEngine.onForegroundPackageChanged(context, app.packageName)
            context.startActivity(launchIntent)
        } else {
            Toast.makeText(context, "无法启动 ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 触发底部 5 大硬件状态切换 (对应 A 键或屏幕触碰点击)
     */
    private fun triggerDockAction(index: Int) {
        when (index) {
            0 -> cyclePerformanceMode()
            1 -> cycleFanMode()
            2 -> toggleJoystickLight()
            3 -> toggleChargePowerLimit()
            4 -> toggleAirplaneMode()
        }
    }

    // --- Config 弹窗手柄动作 ---
    private fun triggerConfigSubAction() {
        when (_configSectionIndex.value) {
            0 -> { // 摇杆灯颜色
                val colors = listOf("#ff00e5ff", "#ff7c4dff", "#ffff5252", "#ff00e676", "#ffffffff", "#ff2e2e2e")
                val selected = colors.getOrNull(_configContentFocusIndex.value % colors.size) ?: "#ff00e5ff"
                setJoystickColor(selected)
            }
            1 -> { // 屏幕方向 (仅支持固定横屏与传感器自适应横屏)
                val orientations = listOf(
                    HardwareController.ORIENTATION_LANDSCAPE,
                    HardwareController.ORIENTATION_SENSOR_LANDSCAPE
                )
                val sel = orientations.getOrNull(_configContentFocusIndex.value % orientations.size)
                    ?: HardwareController.ORIENTATION_LANDSCAPE
                setOrientationMode(sel)
            }
            2 -> { // 默认主屏幕与开机自启
                if (_configContentFocusIndex.value == 0) {
                    requestDefaultHome()
                } else {
                    toggleBootAutoStart()
                }
            }
            3 -> { // 自动风扇控制
                toggleAutoFanControl()
            }
            4 -> { // Tab 页编辑：执行当前光标左右选中的操作按钮
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
            else -> {}
        }
    }

    fun openConfigDialog() {
        refreshHomeAndBootStatus()
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
        _configSectionIndex.value = index
        _configInSubMenu.value = false
        _configContentFocusIndex.value = 0
    }

    fun setOrientationMode(mode: Int) {
        _orientationMode.value = mode
        viewModelScope.launch(Dispatchers.IO) {
            HardwareController.setOrientationMode(context, mode)
        }
    }

    fun toggleBootAutoStart() {
        val next = !_bootAutoStartEnabled.value
        _bootAutoStartEnabled.value = next
        HardwareController.setBootAutoStartEnabled(context, next)
    }

    fun requestDefaultHome() {
        _requestRoleEvent.tryEmit(Unit)
    }

    fun refreshHomeAndBootStatus() {
        _isDefaultHome.value = HardwareController.isDefaultHome(context)
        _bootAutoStartEnabled.value = HardwareController.isBootAutoStartEnabled(context)
    }

    fun cyclePerformanceMode() {
        val current = _performanceMode.value
        val next = if (current in 0..2) (current + 1) % 3 else HardwareController.PERF_NORMAL
        _performanceMode.value = next

        perfJob?.cancel()
        perfJob = viewModelScope.launch(Dispatchers.IO) {
            delay(200)
            hardwareLock.withLock {
                try {
                    HardwareController.setPerformanceMode(context, next)
                    if (next != HardwareController.PERF_NORMAL && _fanMode.value == HardwareController.FAN_OFF) {
                        _fanMode.value = HardwareController.FAN_SMART
                        _autoFanControlEnabled.value = false
                        HardwareController.setFanMode(context, HardwareController.FAN_SMART)
                        HardwareController.setAutoFanControlEnabled(context, false)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("OdinHardware", "Performance mode cycle failed", failure)
                    runCatching { HardwareController.getPerformanceMode(context) }
                        .onSuccess { _performanceMode.value = it }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, failure.message ?: "性能模式切换失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun cycleFanMode() {
        val wasAuto = _autoFanControlEnabled.value
        _autoFanControlEnabled.value = false
        val targetFan = if (wasAuto) {
            HardwareController.FAN_OFF
        } else {
            when (_fanMode.value) {
                HardwareController.FAN_OFF -> HardwareController.FAN_SMART
                HardwareController.FAN_SMART -> HardwareController.FAN_SPORT
                else -> HardwareController.FAN_OFF
            }
        }
        _fanMode.value = targetFan

        fanJob?.cancel()
        fanJob = viewModelScope.launch(Dispatchers.IO) {
            delay(200)
            hardwareLock.withLock {
                try {
                    HardwareController.setAutoFanControlEnabled(context, false)
                    HardwareController.setFanMode(context, targetFan)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("OdinHardware", "Fan mode cycle failed", failure)
                    _autoFanControlEnabled.value = HardwareController.isAutoFanControlEnabled(context)
                    runCatching { HardwareController.getFanMode(context) }
                        .onSuccess { _fanMode.value = it }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, failure.message ?: "风扇档位切换失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun toggleAutoFanControl() {
        val next = !_autoFanControlEnabled.value
        _autoFanControlEnabled.value = next
        if (next) {
            _fanMode.value = HardwareController.FAN_OFF
        }

        fanJob?.cancel()
        fanJob = viewModelScope.launch(Dispatchers.IO) {
            delay(200)
            hardwareLock.withLock {
                try {
                    HardwareController.setAutoFanControlEnabled(context, next)
                    if (next) {
                        HardwareController.setFanMode(context, HardwareController.FAN_OFF)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("OdinHardware", "Auto fan control toggle failed", failure)
                    _autoFanControlEnabled.value = HardwareController.isAutoFanControlEnabled(context)
                    runCatching { HardwareController.getFanMode(context) }
                        .onSuccess { _fanMode.value = it }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, failure.message ?: "充电风扇静音切换失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun toggleJoystickLight() {
        val next = !_joystickLightEnabled.value
        _joystickLightEnabled.value = next

        lightJob?.cancel()
        lightJob = viewModelScope.launch(Dispatchers.IO) {
            delay(150)
            hardwareLock.withLock {
                try {
                    HardwareController.setJoystickLightEnabled(context, next)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("OdinHardware", "Joystick light toggle failed", failure)
                    runCatching { HardwareController.isJoystickLightEnabled(context) }
                        .onSuccess { _joystickLightEnabled.value = it }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, failure.message ?: "摇杆灯切换失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun toggleChargingSeparation() {
        val next = !_chargingSeparation.value
        _chargingSeparation.value = next

        chargeSeparationJob?.cancel()
        chargeSeparationJob = viewModelScope.launch(Dispatchers.IO) {
            delay(150)
            hardwareLock.withLock {
                try {
                    HardwareController.setChargingSeparationEnabled(context, next)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("OdinHardware", "Charging separation toggle failed", failure)
                    runCatching { HardwareController.isChargingSeparationEnabled(context) }
                        .onSuccess { _chargingSeparation.value = it }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, failure.message ?: "充电分离切换失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun toggleChargePowerLimit() {
        val next = !_chargePowerLimit.value
        _chargePowerLimit.value = next

        chargePowerJob?.cancel()
        chargePowerJob = viewModelScope.launch(Dispatchers.IO) {
            delay(150)
            hardwareLock.withLock {
                try {
                    HardwareController.setChargePowerLimit5V(context, next)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("OdinHardware", "Power limit toggle failed", failure)
                    runCatching { HardwareController.isChargePowerLimit5V(context) }
                        .onSuccess { _chargePowerLimit.value = it }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, failure.message ?: "充电功率档位切换失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun toggleAirplaneMode() {
        val next = !_airplaneMode.value
        _airplaneMode.value = next

        airplaneJob?.cancel()
        airplaneJob = viewModelScope.launch(Dispatchers.IO) {
            delay(150)
            hardwareLock.withLock {
                try {
                    HardwareController.setAirplaneMode(context, next)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("OdinHardware", "Airplane mode toggle failed", failure)
                    runCatching { HardwareController.isAirplaneModeOn(context) }
                        .onSuccess { _airplaneMode.value = it }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, failure.message ?: "飞行模式切换失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun refreshSocTemp() {
        viewModelScope.launch(Dispatchers.IO) {
            _currentSocTemp.value = runCatching { HardwareController.getMaxCpuGpuTemp() }.getOrDefault(Float.NaN)
        }
    }

    fun setJoystickColor(hex: String) {
        _joystickColor.value = hex
        lightJob?.cancel()
        lightJob = viewModelScope.launch(Dispatchers.IO) {
            delay(150)
            hardwareLock.withLock {
                try {
                    HardwareController.setJoystickColor(context, hex)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("OdinHardware", "Joystick color set failed", failure)
                    runCatching { HardwareController.getJoystickColor(context) }
                        .onSuccess { _joystickColor.value = it.substringBefore(',') }
                }
            }
        }
    }

    fun addTab(name: String, isGame: Boolean = false) {
        if (_tabs.value.size >= 10) {
            Toast.makeText(context, "最多支持创建 10 个 Tab", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            appRepository.createTab(name, isGame)
        }
    }

    fun renameTab(tab: TabEntity, newName: String) {
        viewModelScope.launch {
            appRepository.updateTab(tab.copy(name = newName))
        }
    }

    fun deleteTab(tab: TabEntity) {
        if (tab.name == "全部应用" || tab.isDefault) {
            Toast.makeText(context, "默认分类不可删除", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "已将「${tab.name}」设为默认首页", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun moveAppToTab(app: InstalledApp, targetTabId: Long) {
        viewModelScope.launch {
            val currentTab = _tabs.value.getOrNull(_selectedTabIndex.value)
            if (currentTab != null && currentTab.name != "全部应用") {
                appRepository.removeAppFromTab(currentTab.id, app.packageName)
            }
            appRepository.addAppToTab(targetTabId, app.packageName)
            closeAppActionDialog()
            val targetName = _tabs.value.find { it.id == targetTabId }?.name ?: "目标分类"
            Toast.makeText(context, "已将「${app.label}」添加到「$targetName」最左侧", Toast.LENGTH_SHORT).show()
        }
    }

    // --- 图标排序编辑模式 (Y 键抖动模式) ---
    fun enterReorderMode() {
        if (_focusZone.value == FocusZone.APPS && _currentTabApps.value.isNotEmpty()) {
            _isReorderingApps.value = true
            _pickedAppIndex.value = null
            Toast.makeText(context, "已进入图标排序模式 (A键抓起/移动，B键退出)", Toast.LENGTH_SHORT).show()
        }
    }

    fun exitReorderMode() {
        _isReorderingApps.value = false
        _pickedAppIndex.value = null
        saveCurrentTabAppOrder()
        Toast.makeText(context, "已保存图标排序", Toast.LENGTH_SHORT).show()
    }

    fun togglePickApp() {
        if (_pickedAppIndex.value == null) {
            _pickedAppIndex.value = _selectedAppIndex.value
        } else {
            _pickedAppIndex.value = null
            saveCurrentTabAppOrder()
        }
    }

    private fun movePickedAppLeft() {
        val currentIndex = _selectedAppIndex.value
        if (currentIndex > 0) {
            val list = _currentTabApps.value.toMutableList()
            val item = list.removeAt(currentIndex)
            list.add(currentIndex - 1, item)
            _currentTabApps.value = list
            _selectedAppIndex.value = currentIndex - 1
            _pickedAppIndex.value = currentIndex - 1
        }
    }

    private fun movePickedAppRight() {
        val currentIndex = _selectedAppIndex.value
        if (currentIndex < _currentTabApps.value.size - 1) {
            val list = _currentTabApps.value.toMutableList()
            val item = list.removeAt(currentIndex)
            list.add(currentIndex + 1, item)
            _currentTabApps.value = list
            _selectedAppIndex.value = currentIndex + 1
            _pickedAppIndex.value = currentIndex + 1
        }
    }

    private fun saveCurrentTabAppOrder() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentTab = _tabs.value.getOrNull(_selectedTabIndex.value) ?: return@launch
            val pkgs = _currentTabApps.value.map { it.packageName }
            appRepository.updateAppOrder(currentTab.id, pkgs)
        }
    }

    fun removeAppFromCurrentTab(app: InstalledApp) {
        val currentTab = _tabs.value.getOrNull(_selectedTabIndex.value) ?: return
        if (currentTab.name == "全部应用") {
            Toast.makeText(context, "【全部应用】为系统全集分类，无法直接移除图标", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            appRepository.removeAppFromTab(currentTab.id, app.packageName)
            closeAppActionDialog()
            Toast.makeText(context, "已从「${currentTab.name}」移除图标", Toast.LENGTH_SHORT).show()
        }
    }

    // --- 应用操作模态框 (Y 键) ---
    fun openAppActionDialog() {
        if (_isDashboardSelected.value || _isConfigOpen.value || _isAppBatchManageDialogOpen.value || _isAppActionDialogOpen.value) return
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
                val currentTab = _tabs.value.getOrNull(_selectedTabIndex.value)
                val targetTabs = _tabs.value.filter { it.id != currentTab?.id && it.name != "全部应用" }
                if (targetTabs.isNotEmpty()) {
                    _appActionInTabPicker.value = true
                    _appActionTabPickerFocusIndex.value = 0
                } else {
                    Toast.makeText(context, "暂无其他可移动的自定义分类", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "无法打开应用详情: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    // --- 批量增删分类应用模态框 (X 键) ---
    fun openBatchManageDialog() {
        if (_isDashboardSelected.value || _isConfigOpen.value || _isAppBatchManageDialogOpen.value || _isAppActionDialogOpen.value || _isReorderingApps.value) return
        val currentTab = _tabs.value.getOrNull(_selectedTabIndex.value)
        if (currentTab != null && currentTab.name == "全部应用") {
            Toast.makeText(context, "【全部应用】由系统自动管理所有已安装应用", Toast.LENGTH_SHORT).show()
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
        val currentTab = _tabs.value.getOrNull(_selectedTabIndex.value) ?: return
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
