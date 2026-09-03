package com.odin.desktop.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.odin.desktop.data.entity.TabEntity
import com.odin.desktop.data.model.InstalledApp
import com.odin.desktop.data.repository.AppRepository
import com.odin.desktop.service.fan.HardwareController
import com.odin.desktop.ui.navigation.FocusZone
import kotlinx.coroutines.Dispatchers
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
    private val _focusZone = MutableStateFlow(FocusZone.APPS)
    val focusZone: StateFlow<FocusZone> = _focusZone.asStateFlow()

    // --- Tab 状态 ---
    private val _tabs = MutableStateFlow<List<TabEntity>>(emptyList())
    val tabs: StateFlow<List<TabEntity>> = _tabs.asStateFlow()

    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    private val _isConfigFocusedInTabs = MutableStateFlow(false)
    val isConfigFocusedInTabs: StateFlow<Boolean> = _isConfigFocusedInTabs.asStateFlow()

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

    private val _performanceMode = MutableStateFlow(HardwareController.PERF_NORMAL)
    val performanceMode: StateFlow<Int> = _performanceMode.asStateFlow()

    private val _fanMode = MutableStateFlow(HardwareController.FAN_SMART)
    val fanMode: StateFlow<Int> = _fanMode.asStateFlow()

    private val _joystickLightEnabled = MutableStateFlow(false)
    val joystickLightEnabled: StateFlow<Boolean> = _joystickLightEnabled.asStateFlow()

    private val _joystickColor = MutableStateFlow("#ff00e5ff")
    val joystickColor: StateFlow<String> = _joystickColor.asStateFlow()

    private val _chargeLimit80 = MutableStateFlow(false)
    val chargeLimit80: StateFlow<Boolean> = _chargeLimit80.asStateFlow()

    private val _airplaneMode = MutableStateFlow(false)
    val airplaneMode: StateFlow<Boolean> = _airplaneMode.asStateFlow()

    private val _orientationMode = MutableStateFlow(HardwareController.ORIENTATION_LANDSCAPE)
    val orientationMode: StateFlow<Int> = _orientationMode.asStateFlow()

    private val _autoFanControlEnabled = MutableStateFlow(true)
    val autoFanControlEnabled: StateFlow<Boolean> = _autoFanControlEnabled.asStateFlow()

    private val _currentSocTemp = MutableStateFlow(40f)
    val currentSocTemp: StateFlow<Float> = _currentSocTemp.asStateFlow()

    // --- Config 弹窗手柄导航状态 ---
    private val _isConfigOpen = MutableStateFlow(false)
    val isConfigOpen: StateFlow<Boolean> = _isConfigOpen.asStateFlow()

    private val _configSectionIndex = MutableStateFlow(0) // 0..4 左侧栏
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
            val perf = HardwareController.getPerformanceMode(context)
            val fan = HardwareController.getFanMode(context)
            val led = HardwareController.isJoystickLightEnabled(context)
            val color = HardwareController.getJoystickColor(context).split(",").firstOrNull() ?: "#ff00e5ff"
            val charge = HardwareController.isChargeLimit80Enabled(context)
            val air = HardwareController.isAirplaneModeOn(context)
            val autoFan = HardwareController.isAutoFanControlEnabled(context)
            val temp = HardwareController.getMaxCpuGpuTemp()

            withContext(Dispatchers.Main) {
                _performanceMode.value = perf
                _fanMode.value = fan
                _joystickLightEnabled.value = led
                _joystickColor.value = color
                _chargeLimit80.value = charge
                _airplaneMode.value = air
                _autoFanControlEnabled.value = autoFan
                _currentSocTemp.value = temp
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
        if (_isConfigOpen.value && _configInSubMenu.value && _configSectionIndex.value == 3) {
            val currentTabs = _tabs.value
            val tab = currentTabs.getOrNull(_configContentFocusIndex.value)
            if (tab != null && _configContentFocusIndex.value > 0) {
                moveTabUp(tab)
                _configContentFocusIndex.value -= 1
            }
            return
        }
        if (_isConfigOpen.value || _isAppActionDialogOpen.value || _isAppBatchManageDialogOpen.value || _isReorderingApps.value) return
        // L1/R1 只在 Tab 之间循环，不选中 CONFIG
        val count = _tabs.value.size
        if (count == 0) return
        val current = _selectedTabIndex.value
        _selectedTabIndex.value = if (current > 0) current - 1 else count - 1
        _isConfigFocusedInTabs.value = false
        _selectedAppIndex.value = 0
        filterAppsForCurrentTab()
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
        if (_isConfigOpen.value || _isAppActionDialogOpen.value || _isAppBatchManageDialogOpen.value || _isReorderingApps.value) return
        // L1/R1 只在 Tab 之间循环，不选中 CONFIG
        val count = _tabs.value.size
        if (count == 0) return
        val current = _selectedTabIndex.value
        _selectedTabIndex.value = if (current < count - 1) current + 1 else 0
        _isConfigFocusedInTabs.value = false
        _selectedAppIndex.value = 0
        filterAppsForCurrentTab()
    }

    fun selectTab(index: Int) {
        if (_isConfigOpen.value || _isAppActionDialogOpen.value || _isAppBatchManageDialogOpen.value || _isReorderingApps.value) return
        if (index in _tabs.value.indices) {
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
                    _isConfigFocusedInTabs.value = false
                    _selectedTabIndex.value = (_tabs.value.size - 1).coerceAtLeast(0)
                } else if (_selectedTabIndex.value > 0) {
                    _selectedTabIndex.value -= 1
                }
                _selectedAppIndex.value = 0
                filterAppsForCurrentTab()
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
                            // 屏幕方向、自动风扇控制、关于等单项/展示页面直接返回左侧菜单
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
                    if (_selectedTabIndex.value < _tabs.value.size - 1) {
                        _selectedTabIndex.value += 1
                    } else {
                        _isConfigFocusedInTabs.value = true
                    }
                }
                _selectedAppIndex.value = 0
                filterAppsForCurrentTab()
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
                    // 屏幕方向、自动风扇控制、关于等右键不执行越界操作
                }
            }
            FocusZone.APP_ACTION_MODAL -> {}
            FocusZone.APP_BATCH_MANAGE_MODAL -> {}
        }
    }

    fun onNavigateUp() {
        when (_focusZone.value) {
            FocusZone.DOCK -> _focusZone.value = FocusZone.APPS
            FocusZone.APPS -> {
                // 排序状态或正常状态下，上键均不离开图标区
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
                    3 -> { // Tab 列表
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
            FocusZone.TABS -> {} // Tab 栏只能用 L1/R1 切换，下键不从 TABS 跳转
            FocusZone.APPS -> {
                // 在排序状态下，光标只能在图标区域中移动，禁止移动到 Dock
                if (!_isReorderingApps.value) {
                    _focusZone.value = FocusZone.DOCK
                }
            }
            FocusZone.DOCK -> {}
            FocusZone.CONFIG_MODAL -> {
                if (!_configInSubMenu.value) {
                    if (_configSectionIndex.value < 4) {
                        _configSectionIndex.value += 1
                    }
                } else when (_configSectionIndex.value) {
                    1 -> { // 屏幕方向（共2项：固定横屏、传感器横屏）
                        if (_configContentFocusIndex.value < 1) {
                            _configContentFocusIndex.value += 1
                        }
                    }
                    2 -> {
                        // 自动风扇控制：仅可选中控制开关 (index 0)，下方为展示内容，不可被光标选中
                    }
                    3 -> { // Tab 列表
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
                    if (_appActionFocusIndex.value < 2) {
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
            FocusZone.TABS -> {
                if (_isConfigFocusedInTabs.value) {
                    openConfigDialog()
                } else {
                    _focusZone.value = FocusZone.APPS
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
                        when (_appActionFocusIndex.value) {
                            0 -> { // 移动到其他 Tab
                                val currentTab = _tabs.value.getOrNull(_selectedTabIndex.value)
                                val targetTabs = _tabs.value.filter { it.id != currentTab?.id && it.name != "全部应用" }
                                if (targetTabs.isNotEmpty()) {
                                    _appActionInTabPicker.value = true
                                    _appActionTabPickerFocusIndex.value = 0
                                } else {
                                    Toast.makeText(context, "暂无其他可移动的自定义分类", Toast.LENGTH_SHORT).show()
                                }
                            }
                            1 -> { // 进入应用属性详情 (系统设置)
                                openAppDetails(app)
                                closeAppActionDialog()
                            }
                            2 -> { // 从当前分类移除
                                val currentTab = _tabs.value.getOrNull(_selectedTabIndex.value)
                                if (currentTab != null && currentTab.name != "全部应用") {
                                    removeAppFromCurrentTab(app)
                                    closeAppActionDialog()
                                } else {
                                    Toast.makeText(context, "【全部应用】为系统全集分类，无法直接移除图标", Toast.LENGTH_SHORT).show()
                                }
                            }
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
            _focusZone.value = FocusZone.APPS // 关闭 CONFIG 后回到应用区
            return true
        }
        if (_focusZone.value == FocusZone.DOCK) {
            _focusZone.value = FocusZone.APPS
            return true
        }
        if (_focusZone.value == FocusZone.APPS && _selectedAppIndex.value > 0) {
            _selectedAppIndex.value = 0
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
            if (tab != null && !tab.isDefault && tab.name != "全部应用") {
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
            Toast.makeText(context, "无法启动 ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 触发底部 5 大硬件状态切换 (对应 A 键或屏幕触碰点击)
     */
    private fun triggerDockAction(index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            when (index) {
                0 -> { // CPU 性能模式：正常 -> 性能 -> 高性能 (联动风扇)
                    val result = HardwareController.cyclePerformanceMode(context)
                    withContext(Dispatchers.Main) {
                        _performanceMode.value = result.perfMode
                        _fanMode.value = result.fanMode
                    }
                }
                1 -> { // 风扇模式：智能 -> 疾风 -> 静音 -> 关闭 (性能不动)
                    val next = HardwareController.cycleFanMode(context)
                    withContext(Dispatchers.Main) { _fanMode.value = next }
                }
                2 -> { // 摇杆灯开关：开 <-> 关
                    val next = HardwareController.toggleJoystickLight(context)
                    withContext(Dispatchers.Main) { _joystickLightEnabled.value = next }
                }
                3 -> { // 80% 充电限制：开 <-> 关
                    val next = HardwareController.toggleChargeLimit80(context)
                    withContext(Dispatchers.Main) { _chargeLimit80.value = next }
                }
                4 -> { // 飞行模式：开 <-> 关
                    val next = HardwareController.toggleAirplaneMode(context)
                    withContext(Dispatchers.Main) { _airplaneMode.value = next }
                }
            }
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
            2 -> { // 自动风扇控制
                toggleAutoFanControl()
            }
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
            else -> {}
        }
    }

    fun openConfigDialog() {
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
        _focusZone.value = FocusZone.APPS // 关闭 CONFIG 后回到应用区
    }

    fun setConfigSection(index: Int) {
        _configSectionIndex.value = index
        _configInSubMenu.value = false
        _configContentFocusIndex.value = 0
    }

    fun setOrientationMode(mode: Int) {
        _orientationMode.value = mode
    }

    fun toggleAutoFanControl() {
        val next = !_autoFanControlEnabled.value
        _autoFanControlEnabled.value = next
        HardwareController.setAutoFanControlEnabled(context, next)
    }

    fun refreshSocTemp() {
        viewModelScope.launch(Dispatchers.IO) {
            val temp = HardwareController.getMaxCpuGpuTemp()
            withContext(Dispatchers.Main) { _currentSocTemp.value = temp }
        }
    }

    fun setJoystickColor(hex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            HardwareController.setJoystickColor(context, hex)
            withContext(Dispatchers.Main) { _joystickColor.value = hex }
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
        _appActionFocusIndex.value = index
    }

    fun setAppActionTabPickerFocusIndex(index: Int) {
        _appActionTabPickerFocusIndex.value = index
    }

    fun executeAppAction(type: com.odin.desktop.ui.components.AppActionType) {
        val app = _appUnderAction.value ?: return
        when (type) {
            com.odin.desktop.ui.components.AppActionType.MOVE_TO_TAB -> {
                val currentTab = _tabs.value.getOrNull(_selectedTabIndex.value)
                val targetTabs = _tabs.value.filter { it.id != currentTab?.id && it.name != "全部应用" }
                if (targetTabs.isNotEmpty()) {
                    _appActionInTabPicker.value = true
                    _appActionTabPickerFocusIndex.value = 0
                } else {
                    Toast.makeText(context, "暂无其他可移动的自定义分类", Toast.LENGTH_SHORT).show()
                }
            }
            com.odin.desktop.ui.components.AppActionType.APP_DETAILS -> {
                openAppDetails(app)
                closeAppActionDialog()
            }
            com.odin.desktop.ui.components.AppActionType.REMOVE_ICON -> {
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
