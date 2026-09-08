package com.odin.desktop.dashboard

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardRefreshTest {
    @Test fun firstVisitSamplesImmediatelyThenThreeFastRefreshesThenSlowRefreshes() = runTest {
        val samples = mutableListOf<Long>()
        dashboardRefreshes().take(6).collect { samples += currentTime }
        assertEquals(listOf(0L, 6_000L, 12_000L, 18_000L, 48_000L, 78_000L), samples)
    }

    @Test fun leavingStopsRefreshesAndReenteringRestartsTheFastPhase() = runTest {
        val samples = mutableListOf<Long>()
        val visit = launch { dashboardRefreshes().collect { samples += currentTime } }
        runCurrent()
        advanceTimeBy(7_000)
        visit.cancelAndJoin()
        advanceTimeBy(60_000)
        assertEquals(listOf(0L, 6_000L), samples)
        val returnedAt = currentTime
        val returned = mutableListOf<Long>()
        dashboardRefreshes().take(5).collect { returned += currentTime - returnedAt }
        assertEquals(listOf(0L, 6_000L, 12_000L, 18_000L, 48_000L), returned)
    }

    @Test fun slowSamplesAreNotOverlappedOrQueuedForCatchUp() = runTest {
        val samples = mutableListOf<Long>()
        dashboardRefreshes().take(3).collect {
            samples += currentTime
            delay(10_000)
        }
        assertEquals(listOf(0L, 16_000L, 32_000L), samples)
    }
}
