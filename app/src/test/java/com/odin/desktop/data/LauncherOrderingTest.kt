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
        val entries = (0..24).map { index ->
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
        app.database.appMappingDao().insertMappings(entries.mapIndexed { index, entry ->
            AppMappingEntity(tabId = categoryId, packageName = entry.activityInfo.packageName, sortOrder = index)
        })
        vm = LauncherViewModel(app)
        store.put("launcher", vm)
        await { vm.tabs.value.size == 2 && vm.allInstalledApps.value.size == 25 }
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
        repeat(30) { vm.onNavigateRight() }
        assertEquals(20, vm.selectedAppIndex.value)
        vm.onConfirm()
        assertTrue(vm.isAllAppsOpen.value)
        await { vm.currentTabApps.value.size == 25 }
        vm.setGridColumns(3)
        vm.onNavigateDown()
        assertEquals(3, vm.selectedAppIndex.value)
        vm.setSortMode(AppSortMode.NAME)
        assertEquals(AppSortMode.NAME, app.appRepository.getSortMode(allId))
        assertEquals(AppSortMode.MANUAL, app.appRepository.getSortMode(categoryId))
        vm.onBack()
        assertFalse(vm.isAllAppsOpen.value)
        assertEquals(0, vm.selectedTabIndex.value)
        assertEquals(20, vm.selectedAppIndex.value)
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
}
