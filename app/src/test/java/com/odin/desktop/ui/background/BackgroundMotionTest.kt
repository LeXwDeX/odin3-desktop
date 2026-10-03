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

    @Test
    fun easedWaveStartsCenteredAndLoopsWithoutAPositionJump() {
        assertEquals(0f, easedWave(0f), .00001f)
        assertEquals(1f, easedWave(.25f), .00001f)
        assertEquals(0f, easedWave(.5f), .00001f)
        assertEquals(-1f, easedWave(.75f), .00001f)
        assertEquals(easedWave(0f), easedWave(1f), .00001f)
        assertEquals(easedWave(.75f), easedWave(-.25f), .00001f)
    }

    @Test
    fun easedWaveSlowsToRestAtTurnsAndKeepsSpeedAcrossLoopBoundary() {
        val step = .005f
        val centerTravel = easedWave(step) - easedWave(0f)
        val turnTravel = easedWave(.25f) - easedWave(.25f - step)
        assertTrue("The ribbon must visibly ease into the turn", turnTravel < centerTravel * .02f)
        assertTrue("The ribbon should keep moving before its turn", easedWave(.20f) < .96f)
        val travelBeforeLoop = easedWave(1f) - easedWave(1f - step)
        assertEquals("The loop must not change speed abruptly", travelBeforeLoop, centerTravel, .0001f)
    }

    @Test
    fun defaultCycleTakesTwentyFourSeconds() {
        val clock = BackgroundMotionClock()
        clock.advance(1_000_000_000L)
        assertEquals(.25f, clock.advance(7_000_000_000L), .00001f)
    }
}
