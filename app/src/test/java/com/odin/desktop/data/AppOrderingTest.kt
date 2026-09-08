package com.odin.desktop.data

import com.odin.desktop.data.model.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class AppOrderingTest {
    private val apps = listOf(
        InstalledApp("z", "Main", "Zebra", firstInstallTime = 20, lastTimeUsed = 300),
        InstalledApp("b", "Main", "beta", firstInstallTime = 30),
        InstalledApp("a", "Main", "Alpha", firstInstallTime = 30, lastTimeUsed = 200)
    )
    private fun List<InstalledApp>.packages() = map { it.packageName }

    @Test fun sortModesUseActualTimestampsAndStableLocaleAwareNames() {
        assertEquals(listOf("a", "b", "z"), sortApps(apps, AppSortMode.INSTALLED, Locale.US).packages())
        assertEquals(listOf("z", "a", "b"), sortApps(apps, AppSortMode.LAST_USED, Locale.US).packages())
        assertEquals(listOf("a", "b", "z"), sortApps(apps, AppSortMode.NAME, Locale.US).packages())
        assertEquals(apps, sortApps(apps, AppSortMode.MANUAL))
        assertEquals(listOf("a", "b", "z"), sortApps(apps.map { it.copy(lastTimeUsed = null) },
            AppSortMode.LAST_USED, Locale.US).packages())
    }

    @Test fun nameTiesDoNotDependOnPackageManagerEnumeration() {
        val sameNames = apps.map { it.copy(label = "same", firstInstallTime = 0) }
        assertEquals(sortApps(sameNames, AppSortMode.NAME), sortApps(sameNames.reversed(), AppSortMode.NAME))
    }

    @Test fun overflowStartsAfterTenAndEditingKeepsEveryAppReachable() {
        assertEquals(0, homeAppCount(0, false))
        assertEquals(9, homeAppCount(9, false))
        assertEquals(10, homeAppCount(10, false))
        assertEquals(11, homeAppCount(11, false))
        assertEquals(11, homeAppCount(20, false))
        assertEquals(11, homeAppCount(50, false))
        assertEquals(50, homeAppCount(50, true))
    }

    @Test fun movingAcrossTheHomeBoundaryPreservesEveryOtherPosition() {
        val many = (0..24).map { InstalledApp("app$it", "Main", "$it") }
        val moved = moveApp(many, 24, 0)
        assertEquals("app24", moved.first().packageName)
        assertEquals(many.dropLast(1), moved.drop(1))
        assertEquals(many, moveApp(moved, 0, 24))
        assertEquals(many, moveApp(many, -1, 0))
        assertEquals(many, moveApp(many, 0, 25))
    }
}
