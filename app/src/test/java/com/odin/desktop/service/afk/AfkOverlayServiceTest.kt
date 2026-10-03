package com.odin.desktop.service.afk

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.os.PowerManager
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityManager
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
        val firstTap = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 1f, 1f, 0)
        root.dispatchTouchEvent(firstTap)
        firstTap.recycle()
        assertTrue("One touch must not expose the game", AfkOverlayService.isAfkRunning)
        assertTrue(wakeLock.isHeld)
        advance(100)
        val secondTap = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 1f, 1f, 0)
        root.dispatchTouchEvent(secondTap)
        secondTap.recycle()
        advance(7000)
        assertNull(field("overlayView"))
        assertFalse(AfkOverlayService.isAfkRunning)
        assertFalse(wakeLock.isHeld)
    }

    @Test fun accessibilityClickExitsWithoutChangingTouchDoubleTapRule() {
        start()
        val root = field("overlayView") as FrameLayout
        val wakeLock = field("wakeLock") as PowerManager.WakeLock
        assertTrue(root.isClickable)
        assertFalse(root.contentDescription.isNullOrBlank())
        shadowOf(service.getSystemService(AccessibilityManager::class.java)).setEnabled(true)
        // Robolectric's WindowManager gives this overlay an inert ViewRootImpl parent,
        // but no AttachInfo. It cannot export a node action list in this unit test.
        // Exercise the real View accessibility action entry point below; device
        // validation must inspect the node exported by the attached overlay.
        if (root.isAttachedToWindow) {
            assertTrue(root.createAccessibilityNodeInfo().actionList.any {
                it.id == AccessibilityNodeInfo.ACTION_CLICK
            })
        }
        assertTrue(root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
        assertNull(field("overlayView"))
        assertFalse(wakeLock.isHeld)
        assertFalse(AfkOverlayService.isAfkRunning)
    }

    @Test fun notificationExitActionStopsServiceWithoutLaunchingActivity() {
        start()
        val wakeLock = field("wakeLock") as PowerManager.WakeLock
        val notifications = service.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(notifications).getNotification(2001)
        assertNotNull(notification)
        assertNull("Notification body has no service click target", notification.contentIntent)
        assertEquals(1, notification.actions.size)
        assertEquals(service.getString(com.odin.desktop.R.string.afk_notification_exit), notification.actions.single().title.toString())
        val exit = notification.actions.single().actionIntent
        assertTrue(exit.isService)
        assertFalse(exit.isActivity)

        exit.send()
        val applicationShadow = shadowOf(service.application)
        val delivered = applicationShadow.nextStartedService
        assertEquals(AfkOverlayService.ACTION_STOP_AFK, delivered.action)
        assertEquals(AfkOverlayService::class.java.name, delivered.component?.className)
        assertNull(applicationShadow.nextStartedActivity)

        // Robolectric records the PendingIntent delivery; dispatch it to the running service.
        service.onStartCommand(delivered, 0, 2)
        assertNull(field("overlayView"))
        assertFalse(wakeLock.isHeld)
        assertFalse(AfkOverlayService.isAfkRunning)
        assertTrue(shadowOf(service).isStoppedBySelf)
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
        shadowOf(power).turnScreenOn(false)
        advance(0)
        assertFalse("System sleep must end AFK", AfkOverlayService.isAfkRunning)
        assertNull(field("overlayView"))
        assertFalse(wakeLock.isHeld)
        assertTrue(shadowOf(service).isStoppedBySelf)
        shadowOf(power).turnScreenOn(true)
        advance(35_000)
        assertNull("Waking must not restore the mask", field("overlayView"))
        assertFalse(AfkOverlayService.isAfkRunning)
        assertFalse(wakeLock.isHeld)
    }

    @Test fun startDeliveredAfterSleepDoesNotCreateMaskOrWakeLock() {
        shadowOf(service.getSystemService(PowerManager::class.java)).turnScreenOn(false)
        start()
        advance(7000)
        assertFalse(AfkOverlayService.isAfkRunning)
        assertNull(field("overlayView"))
        assertNull(field("wakeLock"))
        assertTrue(shadowOf(service).isStoppedBySelf)
    }
}
