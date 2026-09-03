package com.odin.desktop.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.odin.desktop.data.dao.AppMappingDao
import com.odin.desktop.data.dao.TabDao
import com.odin.desktop.data.entity.AppMappingEntity
import com.odin.desktop.data.entity.TabEntity
import com.odin.desktop.data.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class AppRepository(
    private val context: Context,
    private val tabDao: TabDao,
    private val appMappingDao: AppMappingDao
) {

    val allTabs: Flow<List<TabEntity>> = tabDao.getAllTabsFlow()
    val gamePackages: Flow<List<String>> = appMappingDao.getGamePackageNamesFlow()

    suspend fun getInstalledLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        resolveInfos.mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            // 过滤自身桌面
            if (packageName == context.packageName) return@mapNotNull null

            val label = resolveInfo.loadLabel(pm).toString()
            val icon = resolveInfo.loadIcon(pm)
            val appInfo = resolveInfo.activityInfo.applicationInfo
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isGame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appInfo.category == ApplicationInfo.CATEGORY_GAME
            } else {
                false
            }

            InstalledApp(
                packageName = packageName,
                activityName = resolveInfo.activityInfo.name,
                label = label,
                icon = icon,
                isSystemApp = isSystem,
                isGame = isGame
            )
        }.sortedBy { it.label }
    }

    suspend fun createTab(name: String, isGameTab: Boolean = false): Long {
        val currentCount = tabDao.getTabCount()
        val tab = TabEntity(
            name = name,
            sortOrder = currentCount,
            isGameTab = isGameTab
        )
        return tabDao.insertTab(tab)
    }

    suspend fun updateTab(tab: TabEntity) {
        tabDao.updateTab(tab)
    }

    suspend fun moveTabUp(tab: TabEntity) {
        val all = tabDao.getAllTabs().toMutableList()
        val index = all.indexOfFirst { it.id == tab.id }
        if (index > 0) {
            val prev = all[index - 1]
            all[index - 1] = tab.copy(sortOrder = index - 1)
            all[index] = prev.copy(sortOrder = index)
            tabDao.updateTabs(all)
        }
    }

    suspend fun moveTabDown(tab: TabEntity) {
        val all = tabDao.getAllTabs().toMutableList()
        val index = all.indexOfFirst { it.id == tab.id }
        if (index in 0 until all.size - 1) {
            val next = all[index + 1]
            all[index + 1] = tab.copy(sortOrder = index + 1)
            all[index] = next.copy(sortOrder = index)
            tabDao.updateTabs(all)
        }
    }

    suspend fun setDefaultHomeTab(tabId: Long) {
        tabDao.setDefaultTab(tabId)
    }

    suspend fun deleteTab(tabId: Long) {
        tabDao.deleteTabById(tabId)
    }

    suspend fun addAppToTab(tabId: Long, packageName: String) {
        val existing = appMappingDao.getAppsForTab(tabId)
        val updated = mutableListOf<AppMappingEntity>()
        // 默认将新添加的应用置于列表最左侧 (sortOrder = 0)，原有应用依次向右顺延
        val newMapping = existing.find { it.packageName == packageName }?.copy(sortOrder = 0)
            ?: AppMappingEntity(tabId = tabId, packageName = packageName, sortOrder = 0)
        updated.add(newMapping)
        existing.filter { it.packageName != packageName }.forEachIndexed { index, entity ->
            updated.add(entity.copy(sortOrder = index + 1))
        }
        appMappingDao.insertMappings(updated)
    }

    suspend fun removeAppFromTab(tabId: Long, packageName: String) {
        appMappingDao.removeAppFromTab(tabId, packageName)
    }

    suspend fun removeAppFromAllTabs(packageName: String) {
        appMappingDao.removeAppFromAllTabs(packageName)
    }

    fun getAppsForTabFlow(tabId: Long): Flow<List<AppMappingEntity>> {
        return appMappingDao.getAppsForTabFlow(tabId)
    }

    suspend fun updateAppOrder(tabId: Long, packageNames: List<String>) {
        val existing = appMappingDao.getAppsForTab(tabId)
        val updated = mutableListOf<AppMappingEntity>()
        packageNames.forEachIndexed { index, pkg ->
            val match = existing.find { it.packageName == pkg }
            if (match != null) {
                updated.add(match.copy(sortOrder = index))
            } else {
                updated.add(AppMappingEntity(tabId = tabId, packageName = pkg, sortOrder = index))
            }
        }
        appMappingDao.insertMappings(updated)
    }

    suspend fun isGamePackage(packageName: String): Boolean {
        val games = appMappingDao.getGamePackageNames()
        return games.contains(packageName)
    }

    suspend fun sanitizeDefaultTabs() {
        var tabs = tabDao.getAllTabs().toMutableList()
        // 核心兜底：若数据库无 Tab，立即初始化预置默认分类
        if (tabs.isEmpty()) {
            val defaultTabs = listOf(
                TabEntity(name = "游戏与模拟器", sortOrder = 0, isDefault = true, isGameTab = true),
                TabEntity(name = "系统应用", sortOrder = 1, isDefault = false, isGameTab = false),
                TabEntity(name = "全部应用", sortOrder = 2, isDefault = false, isGameTab = false)
            )
            tabDao.insertTabs(defaultTabs)
            tabs = tabDao.getAllTabs().toMutableList()
        }

        // 兼容更名：将原有“系统工具”自动对齐为“系统应用”
        val oldSystemTools = tabs.find { it.name == "系统工具" }
        if (oldSystemTools != null && tabs.none { it.name == "系统应用" }) {
            tabDao.updateTab(oldSystemTools.copy(name = "系统应用"))
            tabs = tabDao.getAllTabs().toMutableList()
        }

        // 确保三大默认 Tab (游戏与模拟器, 系统应用, 全部应用) 齐备
        val currentNames = tabs.map { it.name }.toSet()
        if (!currentNames.contains("游戏与模拟器")) {
            tabDao.insertTab(TabEntity(name = "游戏与模拟器", sortOrder = 0, isDefault = true, isGameTab = true))
        }
        if (!currentNames.contains("系统应用")) {
            tabDao.insertTab(TabEntity(name = "系统应用", sortOrder = 1, isDefault = false, isGameTab = false))
        }
        if (!currentNames.contains("全部应用")) {
            val total = tabDao.getTabCount()
            tabDao.insertTab(TabEntity(name = "全部应用", sortOrder = total, isDefault = false, isGameTab = false))
        }

        val mediaTab = tabs.find { it.name == "影音媒体" }
        if (mediaTab != null) {
            val mappings = appMappingDao.getAppsForTabFlow(mediaTab.id).firstOrNull() ?: emptyList()
            if (mappings.isEmpty()) {
                tabDao.deleteTabById(mediaTab.id)
            }
        }

        // 重新拉取并校准排序：首个 Tab 默认设为首页，【全部应用】固定在末尾
        val updatedTabs = tabDao.getAllTabs().toMutableList()
        val allAppsTab = updatedTabs.find { it.name == "全部应用" }
        if (allAppsTab != null) {
            val others = updatedTabs.filter { it.id != allAppsTab.id }.sortedBy { it.sortOrder }
            val reordered = mutableListOf<TabEntity>()
            others.forEachIndexed { idx, t ->
                reordered.add(t.copy(sortOrder = idx, isDefault = (idx == 0)))
            }
            reordered.add(allAppsTab.copy(sortOrder = others.size, isDefault = false))
            tabDao.updateTabs(reordered)
        }

        autoPopulateDefaultTabMappings()
    }

    suspend fun autoPopulateDefaultTabMappings() {
        val tabs = tabDao.getAllTabs()
        val systemTab = tabs.find { it.name == "系统应用" || it.name == "系统工具" }
        val gameTab = tabs.find { it.isGameTab || it.name == "游戏与模拟器" }
        val apps = getInstalledLaunchableApps()

        // 1. 系统应用：默认摆放本系统的 Google 原生与系统应用
        if (systemTab != null) {
            val existingSystemApps = appMappingDao.getAppsForTabFlow(systemTab.id).firstOrNull() ?: emptyList()
            if (existingSystemApps.isEmpty()) {
                val googleAndSystemPkgs = apps.filter { app ->
                    val pkg = app.packageName
                    pkg.startsWith("com.google.android.") ||
                    pkg.startsWith("com.android.") ||
                    pkg == "com.android.vending" ||
                    pkg == "com.android.chrome" ||
                    pkg == "com.android.settings" ||
                    pkg == "com.odin.settings" ||
                    app.isSystemApp
                }
                for (app in googleAndSystemPkgs) {
                    appMappingDao.insertMapping(AppMappingEntity(tabId = systemTab.id, packageName = app.packageName))
                }
            }
        }

        // 2. 游戏与模拟器：扫描游戏与已知模拟器
        if (gameTab != null) {
            val existingGameApps = appMappingDao.getAppsForTabFlow(gameTab.id).firstOrNull() ?: emptyList()
            if (existingGameApps.isEmpty()) {
                val knownGameOrEmu = apps.filter { app ->
                    app.isGame ||
                    app.packageName.contains("emu", ignoreCase = true) ||
                    app.packageName.contains("mame", ignoreCase = true) ||
                    app.packageName.contains("duckstation", ignoreCase = true) ||
                    app.packageName.contains("ppsspp", ignoreCase = true) ||
                    app.packageName.contains("retroarch", ignoreCase = true) ||
                    app.packageName.contains("armsx2", ignoreCase = true) ||
                    app.packageName.contains("es_de", ignoreCase = true) ||
                    app.packageName.contains("citron", ignoreCase = true) ||
                    app.packageName.contains("yuzu", ignoreCase = true) ||
                    app.packageName.contains("skyemu", ignoreCase = true)
                }
                for (app in knownGameOrEmu) {
                    appMappingDao.insertMapping(AppMappingEntity(tabId = gameTab.id, packageName = app.packageName))
                }
            }
        }
    }
}
