package com.odin.desktop.service.afk

import com.odin.desktop.locale.AppLanguageContext
import com.odin.desktop.locale.AppLanguage
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.odin.desktop.OdinDesktopApplication
import com.odin.desktop.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AfkOverlayService : Service() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(AppLanguageContext.wrap(base))
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: FrameLayout? = null
    private var statusTextView: TextView? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenReceiverRegistered = false
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) stopAfk()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val shifter = BurnInShifterEngine(maxOffsetX = 60, maxOffsetY = 40)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private var lastClickTime = 0L
    private var countdownEndsAt = 0L
    private val countdownRunnable = object : Runnable {
        override fun run() {
            val root = overlayView ?: return
            val remaining = countdownEndsAt - SystemClock.uptimeMillis()
            if (remaining > 0L) {
                statusTextView?.text = getString(R.string.afk_countdown_hint, (remaining + 999L) / 1000L)
                handler.postDelayed(this, minOf(1000L, remaining))
                return
            }
            countdownEndsAt = 0L
            try {
                root.setBackgroundColor(Color.BLACK)
                statusTextView?.apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    setTextColor(Color.parseColor("#333333"))
                }
                val params = root.layoutParams as WindowManager.LayoutParams
                params.screenBrightness = 0.01f
                windowManager.updateViewLayout(root, params)
                handler.post(shiftRunnable)
            } catch (error: RuntimeException) {
                android.util.Log.e("AfkOverlayService", "Could not finish AFK countdown", error)
                stopAfk()
            }
        }
    }

    private val shiftRunnable = object : Runnable {
        override fun run() {
            if (!isAfkRunning || overlayView == null) return
            updateStatusText()
            handler.postDelayed(this, 30_000L) // 每 30 秒漂移一次
        }
    }

    private var languageSubscription: AutoCloseable? = null

    override fun onCreate() {
        super.onCreate()
        languageSubscription = AppLanguage.observeLegacyChanges(::refreshNotificationLanguage)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenOffFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOffReceiver, screenOffFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            // SCREEN_OFF is protected: only the system can send it on older Android.
            registerReceiver(screenOffReceiver, screenOffFilter)
        }
        screenReceiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_AFK) {
            stopAfk()
            return START_NOT_STICKY
        }

        // A queued start can arrive after Power has already put the device to sleep.
        if (!getSystemService(PowerManager::class.java).isInteractive) {
            stopAfk()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.text_overlay_permission_is_unavailable_cannot_start_the), Toast.LENGTH_LONG).show()
            stopAfk()
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification())
            acquireWakeLock()
            showOverlay()
        } catch (error: RuntimeException) {
            android.util.Log.e("AfkOverlayService", "Could not start black overlay", error)
            Toast.makeText(this, getString(R.string.text_idle_screen_failed_to_start_the_picture), Toast.LENGTH_LONG).show()
            stopAfk()
        }
        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
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

        val layoutParams = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
            // Keep the display awake under a black mask without taking keyboard/gamepad focus.
            flags = WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
        }

        val root = object : FrameLayout(this) {
            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    windowInsetsController?.apply {
                        systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        hide(WindowInsets.Type.systemBars())
                    }
                }
            }
        }.apply {
            setBackgroundColor(Color.TRANSPARENT)
            @Suppress("DEPRECATION")
            systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }

        val textView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(230, 32, 32, 32))
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
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

        // Consume touch input on the mask; a double tap returns to the underlying game.
        root.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val now = SystemClock.uptimeMillis()
                if (lastClickTime != 0L && now - lastClickTime < 500) {
                    stopAfk()
                    return@setOnTouchListener true
                }
                lastClickTime = now
            }
            true // 彻底拦截触摸，防止误触底层游戏
        }

        overlayView = root
        windowManager.addView(root, layoutParams)
        isAfkRunning = true
        AfkTileService.requestRefresh(this)
        countdownEndsAt = SystemClock.uptimeMillis() + 6000L
        countdownRunnable.run()
    }

    private fun updateStatusText() {
        val textView = statusTextView ?: return
        val offset = shifter.shift()
        val currentTime = timeFormat.format(Date())

        textView.text = getString(R.string.text_idle_screen_active_value_nvalue, currentTime, getString(R.string.afk_overlay_unlock_hint))
        textView.translationX = offset.first.toFloat()
        textView.translationY = offset.second.toFloat()
    }

    private fun removeOverlay() {
        handler.removeCallbacks(countdownRunnable)
        countdownEndsAt = 0L
        handler.removeCallbacks(shiftRunnable)
        overlayView?.let {
            try {
                windowManager.removeViewImmediate(it)
            } catch (error: RuntimeException) {
                android.util.Log.w("AfkOverlayService", "Could not remove black overlay", error)
            }
            overlayView = null
        }
        statusTextView = null
        lastClickTime = 0L
        isAfkRunning = false
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (error: RuntimeException) {
            android.util.Log.w("AfkOverlayService", "Could not release wake lock", error)
        } finally {
            wakeLock = null
        }
    }

    private fun cleanUp() {
        removeOverlay()
        releaseWakeLock()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (error: RuntimeException) {
            android.util.Log.w("AfkOverlayService", "Could not remove foreground notification", error)
        }
        AfkTileService.requestRefresh(this)
    }

    private fun stopAfk() {
        cleanUp()
        stopSelf()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshNotificationLanguage()
    }

    private fun refreshNotificationLanguage() {
        getSystemService(android.app.NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
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
        if (screenReceiverRegistered) {
            unregisterReceiver(screenOffReceiver)
            screenReceiverRegistered = false
        }
        languageSubscription?.close()
        cleanUp()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP_AFK = "com.odin.desktop.action.STOP_AFK"
        private const val NOTIFICATION_ID = 2001
        @Volatile var isAfkRunning: Boolean = false
            private set
    }
}
