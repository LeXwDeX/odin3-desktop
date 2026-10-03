package com.odin.desktop.ui.state

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisibleStateTest {
    @Test
    fun hiddenUiUnsubscribesAndResumesWithLatestValue() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val owner = object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry.createUnsafe(this)
            }
            val lifecycle = owner.lifecycle
            lifecycle.currentState = Lifecycle.State.CREATED
            val source = MutableStateFlow(1)
            val rendered = mutableListOf<Int>()
            val job = launch { source.collectWhileVisible(lifecycle) { rendered += it } }
            advanceUntilIdle()
            assertEquals(0, source.subscriptionCount.value)

            lifecycle.currentState = Lifecycle.State.STARTED
            advanceUntilIdle()
            assertEquals(listOf(1), rendered)
            assertEquals(1, source.subscriptionCount.value)
            source.value = 2
            advanceUntilIdle()
            assertEquals(listOf(1, 2), rendered)

            lifecycle.currentState = Lifecycle.State.CREATED
            advanceUntilIdle()
            assertEquals(0, source.subscriptionCount.value)
            source.value = 3
            source.value = 4
            advanceUntilIdle()
            assertEquals(listOf(1, 2), rendered)

            lifecycle.currentState = Lifecycle.State.STARTED
            advanceUntilIdle()
            assertEquals(listOf(1, 2, 4), rendered)
            assertEquals(1, source.subscriptionCount.value)
            lifecycle.currentState = Lifecycle.State.DESTROYED
            advanceUntilIdle()
            assertEquals(0, source.subscriptionCount.value)
            assertTrue(job.isCompleted)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
