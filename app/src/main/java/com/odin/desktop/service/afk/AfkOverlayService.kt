package com.odin.desktop.service.afk

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.odin.desktop.OdinDesktopApplication
import com.odin.desktop.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AfkOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: FrameLayout? = null
    private var statusTextView: TextView? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val handler = Handler(Looper.getMainLooper())
    private val shifter = BurnInShifterEngine(maxOffsetX = 60, maxOffsetY = 40)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private var lastClickTime = 0L

    private val shiftRunnable = object : Runnable {
        override fun run() {
            updateStatusText()
            handler.postDelayed(this, 30_000L) // 每 30 秒漂移一次
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_AFK) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        showOverlay()
        return START_STICKY
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "OdinDesktop:AfkOverlayWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L) // 最长保护 12 小时
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        val layoutParams = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
            // 纯黑覆盖，全屏，保持唤醒，最低背光
            flags = WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER
            screenBrightness = 0.01f // 最低亮度
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK) // OLED 物理断电纯黑
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val textView = TextView(this).apply {
            setTextColor(Color.parseColor("#333333")) // 极暗灰，防烧屏
            textSize = 14f
            gravity = Gravity.CENTER
            text = getString(R.string.afk_overlay_unlock_hint)
        }

        statusTextView = textView
        val textParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        root.addView(textView, textParams)

        // 双击解锁机制
        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val now = System.currentTimeMillis()
                if (now - lastClickTime < 500) {
                    stopSelf()
                    return@setOnTouchListener true
                }
                lastClickTime = now
            }
            true // 彻底拦截触摸，防止误触底层游戏
        }

        overlayView = root
        windowManager.addView(root, layoutParams)
        isAfkRunning = true

        handler.post(shiftRunnable)
    }

    private fun updateStatusText() {
        val textView = statusTextView ?: return
        val offset = shifter.shift()
        val currentTime = timeFormat.format(Date())

        textView.text = "挂机运行中 • $currentTime\n${getString(R.string.afk_overlay_unlock_hint)}"
        textView.translationX = offset.first.toFloat()
        textView.translationY = offset.second.toFloat()
    }

    private fun removeOverlay() {
        handler.removeCallbacks(shiftRunnable)
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            overlayView = null
        }
        statusTextView = null
        isAfkRunning = false
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, AfkOverlayService::class.java).apply {
            action = ACTION_STOP_AFK
        }
        val pendingStop = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, OdinDesktopApplication.CHANNEL_AFK)
            .setSmallIcon(R.drawable.ic_tile_afk)
            .setContentTitle(getString(R.string.afk_notification_title))
            .setContentText(getString(R.string.afk_notification_text))
            .setContentIntent(pendingStop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP_AFK = "com.odin.desktop.action.STOP_AFK"
        private const val NOTIFICATION_ID = 2001
        var isAfkRunning: Boolean = false
            private set
    }
}
