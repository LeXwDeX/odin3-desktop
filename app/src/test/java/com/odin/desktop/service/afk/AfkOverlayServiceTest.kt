package com.odin.desktop.service.afk

import android.app.Application
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.os.PowerManager
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSettings
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32, 35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AfkOverlayServiceTest {
    private val controller = Robolectric.buildService(AfkOverlayService::class.java)
    private val service get() = controller.get()

    @Before fun create() {
        ShadowSettings.setCanDrawOverlays(true)
        controller.create()
    }

    @After fun destroy() { controller.destroy() }

    private fun field(name: String): Any? = AfkOverlayService::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(service)

    private fun start() { service.onStartCommand(Intent(), 0, 1) }
    private fun advance(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    @Test fun showsSixSecondCountdownThenBlackWithoutTakingFocus() {
        start()
        val root = field("overlayView") as FrameLayout
        val text = root.getChildAt(0) as TextView
        val wakeLock = field("wakeLock") as PowerManager.WakeLock
        assertTrue(AfkOverlayService.isAfkRunning)
        assertTrue(wakeLock.isHeld)
        assertTrue(text.text.contains("6"))
        assertEquals(Color.TRANSPARENT, (root.background as ColorDrawable).color)
        advance(1000)
        assertTrue(text.text.contains("5"))
        start() // Duplicate starts must not reset the countdown.
        advance(4999)
        assertEquals(Color.TRANSPARENT, (root.background as ColorDrawable).color)
        advance(1)
        assertEquals(Color.BLACK, (root.background as ColorDrawable).color)
        val params = root.layoutParams as WindowManager.LayoutParams
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
        assertEquals(0.01f, params.screenBrightness)
        service.onStartCommand(Intent().setAction(AfkOverlayService.ACTION_STOP_AFK), 0, 2)
        assertFalse(wakeLock.isHeld)
        assertFalse(AfkOverlayService.isAfkRunning)
        assertNull(field("overlayView"))
    }

    @Test fun doubleTapCancelsCountdownAndNeverCreatesLateMask() {
        start()
        val root = field("overlayView") as FrameLayout
        val wakeLock = field("wakeLock") as PowerManager.WakeLock
        advance(1000)
        repeat(2) {
            val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 1f, 1f, 0)
            root.dispatchTouchEvent(event)
            event.recycle()
            advance(100)
        }
        advance(7000)
        assertNull(field("overlayView"))
        assertFalse(AfkOverlayService.isAfkRunning)
        assertFalse(wakeLock.isHeld)
    }

    @Test fun destructionCancelsPendingCountdownAndReleasesWakeLock() {
        start()
        val wakeLock = field("wakeLock") as PowerManager.WakeLock
        service.onDestroy()
        advance(7000)
        assertNull(field("overlayView"))
        assertFalse(wakeLock.isHeld)
    }

    @Test fun screenOffDuringCountdownStopsAfkAndDoesNotResumeOnWake() {
        assertSleepStopsAfk(afterMillis = 1000)
    }

    @Test fun screenOffAfterBlackMaskStopsAfkAndDoesNotResumeOnWake() {
        assertSleepStopsAfk(afterMillis = 7000)
    }

    private fun assertSleepStopsAfk(afterMillis: Long) {
        start()
        advance(afterMillis)
        val wakeLock = field("wakeLock") as PowerManager.WakeLock
        assertTrue(AfkOverlayService.isAfkRunning)
        val power = service.getSystemService(PowerManager::class.java)
        shadowOf(power).setIsInteractive(false)
        service.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        advance(0)
        assertFalse("System sleep must end AFK", AfkOverlayService.isAfkRunning)
        assertNull(field("overlayView"))
        assertFalse(wakeLock.isHeld)
        assertTrue(shadowOf(service).isStoppedBySelf)
        shadowOf(power).setIsInteractive(true)
        service.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        advance(35_000)
        assertNull("Waking must not restore the mask", field("overlayView"))
        assertFalse(AfkOverlayService.isAfkRunning)
        assertFalse(wakeLock.isHeld)
    }

    @Test fun startDeliveredAfterSleepDoesNotCreateMaskOrWakeLock() {
        shadowOf(service.getSystemService(PowerManager::class.java)).setIsInteractive(false)
        start()
        advance(7000)
        assertFalse(AfkOverlayService.isAfkRunning)
        assertNull(field("overlayView"))
        assertNull(field("wakeLock"))
        assertTrue(shadowOf(service).isStoppedBySelf)
    }
}
