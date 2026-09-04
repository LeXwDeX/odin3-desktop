package com.odin.desktop.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.odin.desktop.data.entity.AppMappingEntity
import com.odin.desktop.data.entity.TabEntity
import com.odin.desktop.data.entity.TabKind
import com.odin.desktop.data.db.OdinDatabase
import androidx.room.withTransaction
import com.odin.desktop.data.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AppRepository(
    private val context: Context,
    private val database: OdinDatabase,
    private val classifier: AppClassifier = AndroidAppClassifier
) {

    private val tabDao = database.tabDao()
    private val appMappingDao = database.appMappingDao()

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

    suspend fun moveTabUp(tab: TabEntity) = database.withTransaction {
        val all = tabDao.getAllTabs().toMutableList()
        val index = all.indexOfFirst { it.id == tab.id }
        if (index > 0) {
            val prev = all[index - 1]
            all[index - 1] = all[index].copy(sortOrder = index - 1)
            all[index] = prev.copy(sortOrder = index)
            tabDao.updateTabs(all)
        }
    }

    suspend fun moveTabDown(tab: TabEntity) = database.withTransaction {
        val all = tabDao.getAllTabs().toMutableList()
        val index = all.indexOfFirst { it.id == tab.id }
        if (index in 0 until all.size - 1) {
            val next = all[index + 1]
            all[index + 1] = all[index].copy(sortOrder = index + 1)
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
        val apps = if (tabDao.getTabCount() == 0) getInstalledLaunchableApps() else emptyList()
        database.withTransaction {
            val tabs = tabDao.getAllTabs()
            // 1. 首次启动初始化种子数据 (仅在没有任何 Tab 时执行一次)
            if (tabs.isEmpty()) {
                val defaultTabs = listOf(
                    TabEntity(name = "", kind = TabKind.GAMES, usesDefaultName = true, sortOrder = 0, isDefault = true, isGameTab = true),
                    TabEntity(name = "", kind = TabKind.SYSTEM, usesDefaultName = true, sortOrder = 1),
                    TabEntity(name = "", kind = TabKind.ALL_APPS, usesDefaultName = true, sortOrder = 2)
                )
                tabDao.insertTabs(defaultTabs)
                autoPopulateDefaultTabMappings(apps)
                return@withTransaction
            }

            // 2. 日常启动轻量校验：保留用户的所有自定义设置，仅托底确保有默认首页
            if (tabs.none { it.isDefault }) {
                val first = tabs.minByOrNull { it.sortOrder }
                if (first != null) {
                    tabDao.updateTab(first.copy(isDefault = true))
                }
            }
        }
    }

    private suspend fun autoPopulateDefaultTabMappings(apps: List<InstalledApp>) {
        val tabs = tabDao.getAllTabs()
        val systemTab = tabs.find { it.kind == TabKind.SYSTEM }
        val gameTab = tabs.find { it.kind == TabKind.GAMES }

        // 1. 系统应用：默认摆放本系统的 Google 原生与系统应用
        if (systemTab != null) {
            val existingSystemApps = appMappingDao.getAppsForTab(systemTab.id)
            if (existingSystemApps.isEmpty()) {
                val googleAndSystemPkgs = apps.filter(classifier::isSystem)
                for (app in googleAndSystemPkgs) {
                    appMappingDao.insertMapping(AppMappingEntity(tabId = systemTab.id, packageName = app.packageName))
                }
            }
        }

        // 2. 游戏与模拟器：扫描游戏与已知模拟器
        if (gameTab != null) {
            val existingGameApps = appMappingDao.getAppsForTab(gameTab.id)
            if (existingGameApps.isEmpty()) {
                val knownGameOrEmu = apps.filter(classifier::isGame)
                for (app in knownGameOrEmu) {
                    appMappingDao.insertMapping(AppMappingEntity(tabId = gameTab.id, packageName = app.packageName))
                }
            }
        }
    }
}
