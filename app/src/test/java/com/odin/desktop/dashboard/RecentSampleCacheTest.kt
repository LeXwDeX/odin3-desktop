package com.odin.desktop.dashboard

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecentSampleCacheTest {
    @Test fun returningToThePageReusesRecentSamplesAndRefreshesAtTheExactDeadline() = runTest {
        for (ttl in listOf(30_000L, 60_000L)) {
            val cache = RecentSampleCache<Boolean, Long>(ttl) { currentTime }
            var reads = 0
            val page = flow {
                dashboardRefreshes().collect {
                    emit(cache.sample({ true }) { (++reads).toLong() })
                }
            }
            val first = mutableListOf<Long?>()
            val visit = launch { page.collect { first += it } }
            runCurrent()
            visit.cancelAndJoin()
            advanceTimeBy(ttl - 1)
            assertEquals(listOf(1L), first)
            page.take(1).collect { assertEquals(1L, it) }
            assertEquals(1, reads)
            advanceTimeBy(1)
            page.take(1).collect { assertEquals(2L, it) }
            assertEquals(2, reads)
        }
    }

    @Test fun permissionRevocationInvalidatesSuccessAndRestoringAccessRequiresANewRead() = runTest {
        var permitted = true
        var reads = 0
        val cache = RecentSampleCache<Boolean, Long>(60_000L) { currentTime }
        suspend fun sample() = cache.sample({ permitted }) { allowed ->
            if (allowed) (++reads).toLong() else null
        }
        assertEquals(1L, sample())
        permitted = false
        assertNull(sample())
        assertEquals(1, reads)
        permitted = true
        assertEquals(2L, sample())
    }

    @Test fun permissionChangeDuringAReadDoesNotPublishOrCacheItsResult() = runTest {
        var permitted = true
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val cache = RecentSampleCache<Boolean, Long>(60_000L) { currentTime }
        val pending = async {
            cache.sample({ permitted }) {
                started.complete(Unit)
                finish.await()
                10L
            }
        }
        started.await()
        permitted = false
        finish.complete(Unit)
        assertNull(pending.await())
        permitted = true
        assertEquals(20L, cache.sample({ permitted }) { 20L })
    }

    @Test fun leavingDuringAReadCancelsItAndDoesNotRetainAnIncompleteSample() = runTest {
        val cache = RecentSampleCache<Boolean, Long>(60_000L) { currentTime }
        var reads = 0
        val started = CompletableDeferred<Unit>()
        var cancelledLoader = false
        val visit = launch {
            cache.sample({ true }) {
                reads++
                started.complete(Unit)
                try { delay(60_000); 1L } finally { cancelledLoader = true }
            }
        }
        started.await()
        visit.cancelAndJoin()
        assertTrue(cancelledLoader)
        assertEquals(2L, cache.sample({ true }) { (++reads).toLong() })
    }

    @Test fun overlappingVisitsShareOneLoaderAndStartTheLifetimeAfterItCompletes() = runTest {
        val cache = RecentSampleCache<Boolean, Long>(30_000L) { currentTime }
        var reads = 0
        var active = 0
        var maxActive = 0
        val requests = (0 until 12).map {
            async {
                cache.sample({ true }) {
                    reads++
                    active++
                    maxActive = maxOf(maxActive, active)
                    try { delay(10_000); 100L } finally { active-- }
                }
            }
        }
        assertEquals(List(12) { 100L }, requests.awaitAll())
        assertEquals(1, reads)
        assertEquals(1, maxActive)
        advanceTimeBy(29_999)
        assertEquals(100L, cache.sample({ true }) { fail("Fresh sample reloaded"); null })
        advanceTimeBy(1)
        assertEquals(200L, cache.sample({ true }) { 200L })
    }

    @Test fun failedOrCancelledRefreshCannotFallBackToAnExpiredSuccess() = runTest {
        val cache = RecentSampleCache<Boolean, Long>(30_000L) { currentTime }
        assertEquals(1L, cache.sample({ true }) { 1L })
        advanceTimeBy(30_000)
        assertNull(cache.sample({ true }) { null })
        assertEquals(2L, cache.sample({ true }) { 2L })
        advanceTimeBy(30_000)
        val pending = launch { cache.sample({ true }) { delay(60_000); 3L } }
        runCurrent()
        pending.cancelAndJoin()
        assertEquals(4L, cache.sample({ true }) { 4L })
    }

    @Test fun movingTheClockBackwardsDoesNotExtendTheLifetime() = runTest {
        var now = 100L
        val cache = RecentSampleCache<Boolean, Long>(30_000L) { now }
        assertEquals(1L, cache.sample({ true }) { 1L })
        now = 99L
        assertEquals(2L, cache.sample({ true }) { 2L })
    }
}
