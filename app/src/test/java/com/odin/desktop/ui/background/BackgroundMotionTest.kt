package com.odin.desktop.ui.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundMotionTest {
    private val ready = BackgroundMotionConditions(
        resumed = true,
        windowFocused = true,
        attached = true,
        powerSave = false,
        animatorScale = 1f
    )

    @Test
    fun everyPlatformGateCanStopMotion() {
        assertTrue(ready.allowsMotion(requested = true))
        assertFalse(ready.allowsMotion(requested = false))
        assertFalse(ready.copy(resumed = false).allowsMotion(true))
        assertFalse(ready.copy(windowFocused = false).allowsMotion(true))
        assertFalse(ready.copy(attached = false).allowsMotion(true))
        assertFalse(ready.copy(powerSave = true).allowsMotion(true))
        assertFalse(ready.copy(animatorScale = 0f).allowsMotion(true))
    }

    @Test
    fun pausedClockResumesAtItsPreviousPoseWithoutCountingHiddenTime() {
        val clock = BackgroundMotionClock(periodNanos = 100L)
        assertEquals(0f, clock.advance(1_000L), 0f)
        assertEquals(.25f, clock.advance(1_025L), .0001f)
        clock.pause()

        assertEquals(.25f, clock.advance(9_000L), .0001f)
        assertEquals(.50f, clock.advance(9_025L), .0001f)
    }

    @Test
    fun clockUsesActualFrameElapsedTimeAndWrapsSeamlessly() {
        val clock = BackgroundMotionClock(periodNanos = 100L)
        clock.advance(200L)
        assertEquals(.70f, clock.advance(270L), .0001f)
        assertEquals(.10f, clock.advance(310L), .0001f)
        assertEquals(.10f, clock.advance(310L), .0001f)
        // An out-of-order callback cannot make the ribbon run backwards.
        assertEquals(.10f, clock.advance(300L), .0001f)
    }
}
