package com.odin.desktop.data

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.content.pm.PackageInfo
import androidx.room.Room
import com.odin.desktop.data.db.OdinDatabase
import com.odin.desktop.data.repository.AppRepository
import com.odin.desktop.data.model.InstalledApp
import com.odin.desktop.data.model.orderAllApps
import com.odin.desktop.data.entity.TabEntity
import com.odin.desktop.data.entity.TabKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppRepositoryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val db = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java).build()
    private val repo = AppRepository(context, db)

    @After fun close() = db.close()

    @Test fun newInstallsPrecedeManualOrderAndRemainThereAfterReload() = runBlocking {
        val tab = db.tabDao().insertTab(TabEntity(name = "All", kind = TabKind.ALL_APPS))
        repo.addAppToTab(tab, "old.a")
        repo.addAppToTab(tab, "old.z")
        val apps = listOf(
            InstalledApp("old.a", "Main", "A", firstInstallTime = 10),
            InstalledApp("manual", "Main", "M", firstInstallTime = 30),
            InstalledApp("old.z", "Main", "Z", firstInstallTime = 20),
            InstalledApp("play", "Main", "B", firstInstallTime = 40)
        )
        val reloadedRepo = AppRepository(context, db)
        val saved = reloadedRepo.getAppsForTabFlow(tab).first().map { it.packageName }
        assertEquals(listOf("play", "manual", "old.z", "old.a"),
            orderAllApps(apps, saved).map { it.packageName })
        assertEquals(listOf("play", "manual", "old.z", "old.a"),
            orderAllApps(apps.reversed(), emptyList()).map { it.packageName })
        assertEquals(listOf("manual", "old.a", "old.z", "play"),
            orderAllApps(apps, listOf("manual", "old.a", "old.z", "play", "removed"))
                .map { it.packageName })
    }

    @Test fun staleReorderKeepsAdditionsAndDoesNotResurrectRemovals() = runBlocking {
        val tab = repo.createTab("Games")
        repo.addAppToTab(tab, "old")
        repo.addAppToTab(tab, "kept")
        repo.removeAppFromTab(tab, "old")
        repo.addAppToTab(tab, "new")
        repo.updateAppOrder(tab, listOf("old", "kept", "kept"))
        val result = db.appMappingDao().getAppsForTab(tab)
        assertEquals(listOf("kept", "new"), result.map { it.packageName })
        assertEquals(listOf(0, 1), result.map { it.sortOrder })
    }

    @Test fun renameDoesNotOverwriteNewDefaultOrOrdering() = runBlocking {
        val first = repo.createTab("First")
        val second = repo.createTab("Second")
        val stale = db.tabDao().getTabById(second)!!
        repo.setDefaultHomeTab(second)
        repo.moveTabUp(stale)
        repo.renameTab(stale.id, "Renamed")
        val updated = db.tabDao().getTabById(second)!!
        assertTrue(updated.isDefault)
        assertEquals(0, updated.sortOrder)
        assertEquals("Renamed", updated.name)
        assertFalse(db.tabDao().getTabById(first)!!.isDefault)
    }

    @Test fun concurrentAddsRemainUniqueAndContiguouslyOrdered() = runBlocking {
        val tab = repo.createTab("Apps")
        (0 until 16).map { n -> async(Dispatchers.IO) { repo.addAppToTab(tab, "app.$n") } }.awaitAll()
        val rows = db.appMappingDao().getAppsForTab(tab)
        assertEquals(16, rows.map { it.packageName }.toSet().size)
        assertEquals((0 until 16).toList(), rows.map { it.sortOrder })
        repo.deleteTab(tab)
        repo.addAppToTab(tab, "stale.add")
        repo.updateAppOrder(tab, listOf("stale.add"))
        assertTrue(db.appMappingDao().getAppsForTab(tab).isEmpty())
    }

    @Test fun allAppsCanPersistItsFirstOrderWithoutDuplicateLaunchers() = runBlocking {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val entries = listOf("one", "two", "one").mapIndexed { index, pkg -> ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = pkg
                name = "Activity$index"
                applicationInfo = ApplicationInfo().apply { packageName = pkg }
            }
            nonLocalizedLabel = pkg
        } }
        @Suppress("DEPRECATION")
        shadowOf(context.packageManager).setResolveInfosForIntent(intent, entries)
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = "one"
            firstInstallTime = 123L
            lastUpdateTime = 999L
            applicationInfo = entries.first().activityInfo.applicationInfo
        })
        val tab = db.tabDao().insertTab(TabEntity(name = "All", kind = TabKind.ALL_APPS))
        repo.updateAppOrder(tab, listOf("two", "one", "uninstalled"))
        assertEquals(listOf("two", "one"), db.appMappingDao().getAppsForTab(tab).map { it.packageName })
        assertEquals(listOf("one", "two"), repo.getInstalledLaunchableApps().map { it.packageName })
        assertEquals(123L, repo.getInstalledLaunchableApps().first().firstInstallTime)
    }

    @Test fun disappearedMoveTargetLeavesSourceIntact() = runBlocking {
        val source = repo.createTab("Source")
        val target = repo.createTab("Target")
        repo.addAppToTab(source, "game")
        repo.deleteTab(target)
        assertFalse(repo.moveAppToTab(source, target, "game"))
        assertEquals("game", db.appMappingDao().getAppsForTab(source).single().packageName)
    }
}
