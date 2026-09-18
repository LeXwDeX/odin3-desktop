package com.odin.desktop.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.view.KeyEvent
import androidx.lifecycle.ViewModelStore
import com.odin.desktop.OdinDesktopApplication
import com.odin.desktop.data.entity.AppMappingEntity
import com.odin.desktop.data.entity.TabEntity
import com.odin.desktop.data.entity.TabKind
import com.odin.desktop.ui.navigation.FocusZone
import com.odin.desktop.ui.navigation.GamepadKeyHandler
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
class GamepadKeyHandlerYTest {
    private val app = RuntimeEnvironment.getApplication() as OdinDesktopApplication
    private val store = ViewModelStore()
    private lateinit var vm: LauncherViewModel
    private var categoryId = 0L

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
        app.database.tabDao().insertTab(TabEntity(name = "All", kind = TabKind.ALL_APPS, sortOrder = 1))
        app.database.tabDao().insertTab(TabEntity(name = "Empty", sortOrder = 2))
        app.database.appMappingDao().insertMappings(entries.take(25).mapIndexed { index, entry ->
            AppMappingEntity(tabId = categoryId, packageName = entry.activityInfo.packageName, sortOrder = index)
        })
        vm = LauncherViewModel(app)
        store.put("launcher", vm)
        await { vm.tabs.value.size == 3 && vm.allInstalledApps.value.size == 31 }
        vm.selectTab(0)
        await { vm.currentTabApps.value.size == 25 }
        assertEquals(FocusZone.APPS, vm.focusZone.value)
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

    private fun key(action: Int, code: Int, time: Long, repeat: Int = 0): KeyEvent =
        KeyEvent(0L, time, action, code, repeat)

    private fun tap(code: Int, down: Long) {
        GamepadKeyHandler.handleKeyEvent(key(KeyEvent.ACTION_DOWN, code, down), vm)
        GamepadKeyHandler.handleKeyEvent(key(KeyEvent.ACTION_UP, code, down + 100), vm)
    }

    /** Hold past the long-press threshold using framework key repeats, then release. */
    private fun holdWithRepeats(code: Int, down: Long) {
        GamepadKeyHandler.handleKeyEvent(key(KeyEvent.ACTION_DOWN, code, down), vm)
        GamepadKeyHandler.handleKeyEvent(key(KeyEvent.ACTION_DOWN, code, down + 400, repeat = 1), vm)
        GamepadKeyHandler.handleKeyEvent(key(KeyEvent.ACTION_UP, code, down + 500), vm)
    }

    @Test fun shortYOnSelectedIconOpensTheAppActionMenu() {
        tap(KeyEvent.KEYCODE_BUTTON_Y, down = 1000)
        assertTrue(vm.isAppActionDialogOpen.value)
        assertEquals(vm.currentTabApps.value[0], vm.appUnderAction.value)
        assertEquals(FocusZone.APP_ACTION_MODAL, vm.focusZone.value)
        vm.closeAppActionDialog()
    }

    @Test fun shortYOnFocusedIconUsesTheCurrentSelectionNotAStaleOne() {
        repeat(7) { vm.onNavigateRight() }
        val expected = vm.currentTabApps.value[vm.selectedAppIndex.value]
        tap(KeyEvent.KEYCODE_BUTTON_Y, down = 1000)
        assertEquals(expected, vm.appUnderAction.value)
        vm.closeAppActionDialog()
    }

    @Test fun longYWithRepeatsEntersReorderAndReleaseNeverOpensTheMenu() {
        GamepadKeyHandler.handleKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_Y, 1000), vm)
        GamepadKeyHandler.handleKeyEvent(
            key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_Y, 1400, repeat = 1), vm)
        assertTrue(vm.isReorderingApps.value)
        assertFalse(vm.isAppActionDialogOpen.value)
        GamepadKeyHandler.handleKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_Y, 1500), vm)
        assertTrue(vm.isReorderingApps.value)
        assertFalse(vm.isAppActionDialogOpen.value)
        vm.exitReorderMode()
    }

    @Test fun longYWithoutRepeatsEntersReorderOnReleaseAndNeverOpensTheMenu() {
        GamepadKeyHandler.handleKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_Y, 1000), vm)
        GamepadKeyHandler.handleKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_Y, 1700), vm)
        assertTrue(vm.isReorderingApps.value)
        assertFalse(vm.isAppActionDialogOpen.value)
        vm.exitReorderMode()
    }

    @Test fun shortYWhileReorderingExitsInsteadOfOpeningTheMenu() {
        holdWithRepeats(KeyEvent.KEYCODE_BUTTON_Y, down = 1000)
        assertTrue(vm.isReorderingApps.value)
        tap(KeyEvent.KEYCODE_BUTTON_Y, down = 3000)
        assertFalse(vm.isReorderingApps.value)
        assertFalse(vm.isAppActionDialogOpen.value)
    }

    @Test fun yOnTheHomePlusTileIsIgnoredForShortAndLongPresses() {
        repeat(10) { vm.onNavigateRight() }
        assertEquals(10, vm.selectedAppIndex.value)
        tap(KeyEvent.KEYCODE_BUTTON_Y, down = 1000)
        assertFalse(vm.isAppActionDialogOpen.value)
        assertFalse(vm.isReorderingApps.value)
        holdWithRepeats(KeyEvent.KEYCODE_BUTTON_Y, down = 2000)
        assertFalse(vm.isAppActionDialogOpen.value)
        assertFalse(vm.isReorderingApps.value)
        assertEquals(10, vm.selectedAppIndex.value)
    }

    @Test fun yOutsideTheAppsZoneNeverTriggersStaleIconActions() {
        vm.onNavigateDown()
        assertEquals(FocusZone.DOCK, vm.focusZone.value)
        tap(KeyEvent.KEYCODE_BUTTON_Y, down = 1000)
        holdWithRepeats(KeyEvent.KEYCODE_BUTTON_Y, down = 2000)
        assertFalse(vm.isAppActionDialogOpen.value)
        assertFalse(vm.isReorderingApps.value)
        vm.onBack()
        vm.onNavigateUp()
        assertEquals(FocusZone.TABS, vm.focusZone.value)
        tap(KeyEvent.KEYCODE_BUTTON_Y, down = 4000)
        assertFalse(vm.isAppActionDialogOpen.value)
        assertFalse(vm.isReorderingApps.value)
        vm.selectDashboard()
        tap(KeyEvent.KEYCODE_BUTTON_Y, down = 5000)
        assertFalse(vm.isAppActionDialogOpen.value)
        assertFalse(vm.isReorderingApps.value)
    }

    @Test fun yWithSortMenuOrActionDialogOpenChangesNothing() {
        vm.openSortMenu()
        assertTrue(vm.isSortMenuOpen.value)
        tap(KeyEvent.KEYCODE_BUTTON_Y, down = 1000)
        assertTrue(vm.isSortMenuOpen.value)
        assertFalse(vm.isAppActionDialogOpen.value)
        assertFalse(vm.isReorderingApps.value)
        vm.closeSortMenu()
        tap(KeyEvent.KEYCODE_BUTTON_Y, down = 2000)
        assertTrue(vm.isAppActionDialogOpen.value)
        val subject = vm.appUnderAction.value
        tap(KeyEvent.KEYCODE_BUTTON_Y, down = 3000)
        assertTrue(vm.isAppActionDialogOpen.value)
        assertEquals(subject, vm.appUnderAction.value)
        vm.closeAppActionDialog()
    }

    @Test fun shortYInsideTheAllAppsLibraryOpensTheMenuForValidIcons() {
        repeat(10) { vm.onNavigateRight() }
        vm.onConfirm()
        assertTrue(vm.isAllAppsOpen.value)
        await { vm.currentTabApps.value.size == 25 }
        repeat(12) { vm.onNavigateDown() }
        val expected = vm.currentTabApps.value[vm.selectedAppIndex.value]
        assertTrue(vm.selectedAppIndex.value >= 10)
        tap(KeyEvent.KEYCODE_BUTTON_Y, down = 1000)
        assertTrue(vm.isAppActionDialogOpen.value)
        assertEquals(expected, vm.appUnderAction.value)
        vm.closeAppActionDialog()
    }

    @Test fun focusChangeDuringAYHoldDoesNotTriggerStaleIconActions() {
        GamepadKeyHandler.handleKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_Y, 1000), vm)
        vm.onNavigateDown()
        assertEquals(FocusZone.DOCK, vm.focusZone.value)
        GamepadKeyHandler.handleKeyEvent(
            key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_Y, 1400, repeat = 1), vm)
        GamepadKeyHandler.handleKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_Y, 1500), vm)
        assertFalse(vm.isAppActionDialogOpen.value)
        assertFalse(vm.isReorderingApps.value)
    }

    @Test fun canceledYReleaseIsIgnoredAndClearsThePendingPress() {
        GamepadKeyHandler.handleKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_Y, 1000), vm)
        GamepadKeyHandler.handleKeyEvent(
            KeyEvent(0L, 1100, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_Y, 0, 0, 0, 0,
                KeyEvent.FLAG_CANCELED, android.view.InputDevice.SOURCE_KEYBOARD), vm)
        assertFalse(vm.isAppActionDialogOpen.value)
        assertFalse(vm.isReorderingApps.value)
        // The canceled release consumed the press: a duplicated later UP must not fire either.
        GamepadKeyHandler.handleKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_Y, 1200), vm)
        assertFalse(vm.isAppActionDialogOpen.value)
        assertFalse(vm.isReorderingApps.value)
    }

    @Test fun yOnAnEmptyCategoryIsIgnored() {
        vm.selectTab(2)
        await { vm.currentTabApps.value.isEmpty() }
        tap(KeyEvent.KEYCODE_BUTTON_Y, down = 1000)
        holdWithRepeats(KeyEvent.KEYCODE_BUTTON_Y, down = 2000)
        assertFalse(vm.isAppActionDialogOpen.value)
        assertFalse(vm.isReorderingApps.value)
    }

    @Test fun xKeyKeepsOpeningBatchManageForShortAndLongPresses() {
        tap(KeyEvent.KEYCODE_BUTTON_X, down = 1000)
        assertTrue(vm.isAppBatchManageDialogOpen.value)
        vm.onBack()
        assertFalse(vm.isAppBatchManageDialogOpen.value)
        GamepadKeyHandler.handleKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_X, 2000), vm)
        GamepadKeyHandler.handleKeyEvent(
            key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_X, 2400, repeat = 1), vm)
        assertTrue(vm.isAppBatchManageDialogOpen.value)
        GamepadKeyHandler.handleKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_X, 2500), vm)
        assertTrue(vm.isAppBatchManageDialogOpen.value)
        vm.closeBatchManageDialog()
    }
}
