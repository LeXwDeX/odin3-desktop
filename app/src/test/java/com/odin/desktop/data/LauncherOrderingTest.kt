package com.odin.desktop.data

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import androidx.lifecycle.ViewModelStore
import com.odin.desktop.OdinDesktopApplication
import com.odin.desktop.data.entity.AppMappingEntity
import com.odin.desktop.data.entity.TabEntity
import com.odin.desktop.data.entity.TabKind
import com.odin.desktop.data.model.AppSortMode
import com.odin.desktop.ui.viewmodel.LauncherViewModel
import com.odin.desktop.ui.navigation.FocusZone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OdinDesktopApplication::class)
class LauncherOrderingTest {
    private val app = RuntimeEnvironment.getApplication() as OdinDesktopApplication
    private val store = ViewModelStore()
    private lateinit var vm: LauncherViewModel
    private var categoryId = 0L
    private var allId = 0L

    @Before fun prepare() = runBlocking {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.database.clearAllTables() }
        app.getSharedPreferences("app_display", 0).edit().clear().commit()
        val pm = shadowOf(app.packageManager)
        val entries = (0..30).map { index ->
            val pkg = "test.app${index.toString().padStart(2, '0')}"
            val info = ApplicationInfo().apply { packageName = pkg; name = pkg }
            pm.installPackage(PackageInfo().apply {
                packageName = pkg; applicationInfo = info; firstInstallTime = 100L - index
            })
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply { packageName = pkg; name = "Main"; applicationInfo = info }
                nonLocalizedLabel = "App ${index.toString().padStart(2, '0')}"
            }
        }
        @Suppress("DEPRECATION")
        pm.setResolveInfosForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), entries)
        categoryId = app.database.tabDao().insertTab(TabEntity(name = "Games", isDefault = true))
        allId = app.database.tabDao().insertTab(TabEntity(name = "All", kind = TabKind.ALL_APPS, sortOrder = 1))
        app.database.appMappingDao().insertMappings(entries.take(25).mapIndexed { index, entry ->
            AppMappingEntity(tabId = categoryId, packageName = entry.activityInfo.packageName, sortOrder = index)
        })
        vm = LauncherViewModel(app)
        store.put("launcher", vm)
        await { vm.tabs.value.size == 2 && vm.allInstalledApps.value.size == 31 }
        vm.selectTab(0)
        await { vm.currentTabApps.value.size == 25 }
    }

    @After fun finish() { store.clear() }

    private fun await(condition: () -> Boolean) {
        repeat(500) {
            ShadowLooper.idleMainLooper()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("Launcher state did not settle")
    }

    @Test fun touchPlacementAndDragSaveCompleteOrderAndDoNotLaunch() {
        val original = vm.currentTabApps.value
        vm.pickAppForDrag(original[24].packageName)
        assertTrue(vm.isReorderingApps.value)
        vm.onAppClick(original[0], 0)
        assertEquals(original[24], vm.currentTabApps.value.first())
        assertNull(vm.pickedAppIndex.value)
        vm.pickAppForDrag(original[24].packageName)
        vm.moveDraggedApp(original[24].packageName, original[2].packageName)
        vm.finishAppDrag()
        val finalOrder = vm.currentTabApps.value.map { it.packageName }
        vm.exitReorderMode()
        await {
            runBlocking { app.database.appMappingDao().getAppsForTab(categoryId).map { it.packageName } } == finalOrder
        }
        vm.selectTab(1)
        vm.selectTab(0)
        await { vm.currentTabApps.value.map { it.packageName } == finalOrder }
        assertEquals(25, finalOrder.toSet().size)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun plusAndGridNavigationReturnToSameCategoryPositionAndSortPersistsSeparately() {
        val categoryPackages = vm.currentTabApps.value.map { it.packageName }.toSet()
        repeat(30) { vm.onNavigateRight() }
        assertEquals(10, vm.selectedAppIndex.value)
        vm.onConfirm()
        assertTrue(vm.isAllAppsOpen.value)
        await { vm.currentTabApps.value.size == 25 }
        assertEquals(categoryPackages, vm.currentTabApps.value.map { it.packageName }.toSet())
        vm.setGridColumns(3)
        vm.onNavigateUp()
        vm.onPrevTab()
        vm.onNextTab()
        vm.onDockItemClick(0)
        assertTrue(vm.isAllAppsOpen.value)
        assertEquals(FocusZone.APPS, vm.focusZone.value)
        assertEquals(0, vm.selectedTabIndex.value)
        assertEquals(0, vm.selectedAppIndex.value)
        vm.onNavigateDown()
        assertEquals(3, vm.selectedAppIndex.value)
        vm.setSortMode(AppSortMode.NAME)
        assertEquals(AppSortMode.NAME, app.appRepository.getSortMode(categoryId))
        assertEquals(AppSortMode.MANUAL, app.appRepository.getSortMode(allId))
        vm.onBack()
        assertFalse(vm.isAllAppsOpen.value)
        assertEquals(0, vm.selectedTabIndex.value)
        assertEquals(10, vm.selectedAppIndex.value)
        vm.onConfirm()
        await { vm.sortMode.value == AppSortMode.NAME }
        vm.enterReorderMode()
        assertEquals(AppSortMode.MANUAL, vm.sortMode.value)
        vm.onBack()
        assertFalse(vm.isReorderingApps.value)
        assertTrue(vm.isAllAppsOpen.value)
        vm.onBack()
        assertFalse(vm.isAllAppsOpen.value)
    }

    @Test fun gridBoundaryAndNestedDialogsKeepFocusUntilBackReturnsToPlus() {
        repeat(10) { vm.onNavigateRight() }
        vm.onConfirm()
        await { vm.currentTabApps.value.size == 25 }
        vm.setGridColumns(6)
        repeat(20) { vm.onNavigateDown() }
        assertEquals(24, vm.selectedAppIndex.value)
        assertEquals(FocusZone.APPS, vm.focusZone.value)
        vm.openSortMenu()
        vm.onBack()
        assertFalse(vm.isSortMenuOpen.value)
        assertTrue(vm.isAllAppsOpen.value)
        vm.openAppActionDialog()
        vm.onBack()
        assertFalse(vm.isAppActionDialogOpen.value)
        assertTrue(vm.isAllAppsOpen.value)
        vm.onBack()
        assertFalse(vm.isAllAppsOpen.value)
        assertEquals(10, vm.selectedAppIndex.value)
        assertEquals(FocusZone.APPS, vm.focusZone.value)
    }

    @Test fun expandedCategorySavesItsOwnOrderAndMembership() {
        val original = vm.currentTabApps.value
        vm.openAllApps()
        vm.pickAppForDrag(original.last().packageName)
        vm.moveDraggedApp(original.last().packageName, original.first().packageName)
        vm.finishAppDrag()
        val reordered = vm.currentTabApps.value.map { it.packageName }
        vm.exitReorderMode()
        await {
            runBlocking { app.database.appMappingDao().getAppsForTab(categoryId).map { it.packageName } } == reordered
        }
        assertEquals(original.last().packageName, reordered.first())
        assertEquals(25, reordered.size)
        assertTrue(runBlocking { app.database.appMappingDao().getAppsForTab(allId) }.isEmpty())

        vm.removeAppFromCurrentTab(original.first())
        await {
            runBlocking { app.database.appMappingDao().getAppsForTab(categoryId) }.size == 24 &&
                original.first().packageName !in vm.currentTabAppPackages.value
        }
        vm.openBatchManageDialog()
        assertTrue(vm.isAppBatchManageDialogOpen.value)
        val outsideApp = vm.allInstalledApps.value.first { it.packageName !in original.map { app -> app.packageName } }
        vm.toggleAppInCurrentTab(outsideApp)
        await { outsideApp.packageName in vm.currentTabAppPackages.value }
        assertTrue(runBlocking { app.database.appMappingDao().getAppsForTab(allId) }.isEmpty())
        assertEquals(31, vm.allInstalledApps.value.size)
        vm.onBack()
        assertTrue(vm.isAllAppsOpen.value)
        vm.onBack()
        await { vm.currentTabApps.value.size == 25 }
        assertEquals(10, vm.selectedAppIndex.value)
        assertTrue(outsideApp.packageName in vm.currentTabAppPackages.value)
        assertFalse(original.first().packageName in vm.currentTabAppPackages.value)
    }

    @Test fun expandingAllAppsTabStillShowsEveryInstalledApp() {
        vm.selectTab(1)
        await { vm.currentTabApps.value.size == 31 }
        vm.openAllApps()
        assertEquals(vm.allInstalledApps.value.map { it.packageName }.toSet(),
            vm.currentTabApps.value.map { it.packageName }.toSet())
        vm.setSortMode(AppSortMode.NAME)
        assertEquals(AppSortMode.NAME, app.appRepository.getSortMode(allId))
        assertEquals(AppSortMode.MANUAL, app.appRepository.getSortMode(categoryId))
        vm.onBack()
        assertEquals(1, vm.selectedTabIndex.value)
        assertEquals(10, vm.selectedAppIndex.value)
    }
}
