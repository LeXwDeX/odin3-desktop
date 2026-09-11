package com.odin.desktop.data.repository

import android.content.Context
import com.odin.desktop.data.model.AppSortMode
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AppRepository(
    private val context: Context,
    private val database: OdinDatabase,
    private val classifier: AppClassifier = AndroidAppClassifier
) {
    private val displayPreferences = context.getSharedPreferences("app_display", Context.MODE_PRIVATE)
    var usageStatsAvailable: Boolean = false
        private set

    fun getSortMode(tabId: Long): AppSortMode =
        AppSortMode.entries.firstOrNull {
            it.name == displayPreferences.getString("sort_$tabId", null)
        } ?: AppSortMode.MANUAL

    fun setSortMode(tabId: Long, mode: AppSortMode) {
        displayPreferences.edit().putString("sort_$tabId", mode.name).apply()
    }

    private fun lastUsedTimes(): Map<String, Long> {
        val ops = context.getSystemService(android.app.AppOpsManager::class.java)
        usageStatsAvailable = ops?.unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName
        ) == android.app.AppOpsManager.MODE_ALLOWED
        if (!usageStatsAvailable) return emptyMap()
        return try {
            val now = System.currentTimeMillis()
            val usage = context.getSystemService(android.app.usage.UsageStatsManager::class.java)
            val records = usage?.queryAndAggregateUsageStats(now - 365L * 24 * 60 * 60 * 1000, now)
            if (records == null) usageStatsAvailable = false
            records.orEmpty().mapValues { it.value.lastTimeUsed }.filterValues { it > 0L }
        } catch (_: SecurityException) {
            usageStatsAvailable = false
            emptyMap()
        }
    }

    private val tabDao = database.tabDao()
    private val appMappingDao = database.appMappingDao()

    val allTabs: Flow<List<TabEntity>> = tabDao.getAllTabsFlow()
    val gamePackages: Flow<List<String>> = appMappingDao.getGamePackageNamesFlow()

    suspend fun getInstalledLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val lastUsed = lastUsedTimes()
        launchableActivities().distinctBy { it.activityInfo?.packageName }.mapNotNull { resolveInfo ->
            val activity = resolveInfo.activityInfo ?: return@mapNotNull null
            val packageName = activity.packageName
            if (packageName == context.packageName) return@mapNotNull null
            val label = runCatching { resolveInfo.loadLabel(pm).toString() }.getOrDefault(packageName)
            val icon = runCatching { resolveInfo.loadIcon(pm) }.getOrElse { pm.defaultActivityIcon }
            val appInfo = activity.applicationInfo
            val firstInstallTime = runCatching {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0).firstInstallTime
            }.getOrDefault(0L)
            InstalledApp(packageName, activity.name, label, icon,
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                appInfo.category == ApplicationInfo.CATEGORY_GAME,
                firstInstallTime, lastUsed[packageName])
        }.sortedBy { it.label }
    }

    private fun launchableActivities(): List<android.content.pm.ResolveInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

    }

    suspend fun createTab(name: String, isGameTab: Boolean = false): Long = database.withTransaction {
        val nextOrder = (tabDao.getAllTabs().maxOfOrNull { it.sortOrder } ?: -1) + 1
        val tab = TabEntity(
            name = name,
            sortOrder = nextOrder,
            isGameTab = isGameTab
        )
        tabDao.insertTab(tab)
    }

    suspend fun renameTab(tabId: Long, name: String) {
        tabDao.renameTab(tabId, name)
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

    suspend fun addAppToTab(tabId: Long, packageName: String) = database.withTransaction {
        if (tabDao.getTabById(tabId) == null) return@withTransaction
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

    suspend fun moveAppToTab(sourceTabId: Long?, targetTabId: Long, packageName: String): Boolean = database.withTransaction {
        if (tabDao.getTabById(targetTabId) == null) return@withTransaction false
        addAppToTab(targetTabId, packageName)
        if (sourceTabId != null && sourceTabId != targetTabId) {
            appMappingDao.removeAppFromTab(sourceTabId, packageName)
        }
        true
    }

    suspend fun removeAppFromAllTabs(packageName: String) {
        appMappingDao.removeAppFromAllTabs(packageName)
    }

    suspend fun removeUninstalledAppMappings(launchablePackages: Set<String>) = withContext(Dispatchers.IO) {
        // Without full package visibility, NameNotFound can mean hidden rather than uninstalled.
        if (Build.VERSION.SDK_INT >= 30 && context.checkSelfPermission(
                android.Manifest.permission.QUERY_ALL_PACKAGES
            ) != PackageManager.PERMISSION_GRANTED) return@withContext
        val removed = appMappingDao.getMappedPackageNames().filter { packageName ->
            currentCoroutineContext().ensureActive()
            if (packageName in launchablePackages) return@filter false
            try {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
                false
            } catch (_: PackageManager.NameNotFoundException) {
                true
            } catch (_: RuntimeException) {
                // A failed package-service query is not proof of removal.
                false
            }
        }
        if (removed.isNotEmpty()) database.withTransaction {
            // Stay below SQLite's bind limit while committing the cleanup atomically.
            removed.chunked(900).forEach { appMappingDao.removeAppsFromAllTabs(it) }
        }
    }

    fun getAppsForTabFlow(tabId: Long): Flow<List<AppMappingEntity>> {
        return appMappingDao.getAppsForTabFlow(tabId)
    }

    suspend fun updateAppOrder(tabId: Long, packageNames: List<String>) = database.withTransaction {
        val tab = tabDao.getTabById(tabId) ?: return@withTransaction
        val existing = appMappingDao.getAppsForTab(tabId)
        val byPackage = existing.associateBy { it.packageName }.toMutableMap()
        // The automatic All Apps tab has no membership rows until its first reorder.
        if (tab.kind == TabKind.ALL_APPS) {
            val installed = launchableActivities().mapNotNull { it.activityInfo?.packageName }.toSet() - context.packageName
            packageNames.distinct().filter { it in installed }.forEach { pkg ->
                byPackage.putIfAbsent(pkg, AppMappingEntity(tabId = tabId, packageName = pkg))
            }
        }
        // A stale drag result must neither resurrect removed apps nor lose concurrent additions.
        val order = (packageNames + existing.map { it.packageName }).distinct().filter { it in byPackage }
        val updated = order.mapIndexed { index, pkg ->
            byPackage.getValue(pkg).copy(sortOrder = index)
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
