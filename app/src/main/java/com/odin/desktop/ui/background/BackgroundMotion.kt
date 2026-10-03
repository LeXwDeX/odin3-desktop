package com.odin.desktop.ui.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.floor

/** The caller supplies its own modal or screen visibility gate through motionEnabled. */
internal data class BackgroundMotionConditions(
    val resumed: Boolean,
    val windowFocused: Boolean,
    val attached: Boolean,
    val powerSave: Boolean,
    val animatorScale: Float
) {
    fun allowsMotion(requested: Boolean): Boolean =
        requested && resumed && windowFocused && attached && !powerSave && animatorScale > 0f
}

/** Keeps the current pose when paused; elapsed time outside active frames never advances it. */
internal class BackgroundMotionClock(private val periodNanos: Long = 24_000_000_000L) {
    init { require(periodNanos > 0L) }

    var phase: Float = 0f
        private set
    private var previousFrameNanos: Long? = null

    fun advance(frameNanos: Long): Float {
        val previous = previousFrameNanos
        if (previous == null) {
            previousFrameNanos = frameNanos
        } else if (frameNanos > previous) {
            previousFrameNanos = frameNanos
            val elapsed = frameNanos - previous
            // Modulo before conversion preserves precision over long sessions.
            phase = ((phase.toDouble() + (elapsed % periodNanos).toDouble() / periodNanos) % 1.0).toFloat()
        }
        return phase
    }

    fun pause() {
        previousFrameNanos = null
    }
}

/** Smooth, seamless ping-pong motion: center at 0/0.5, resting at +/-1 on quarter turns. */
internal fun easedWave(phase: Float): Float {
    val wrapped = phase - floor(phase)
    val position = (wrapped + .25f) % 1f
    val rise = if (position <= .5f) position * 2f else (1f - position) * 2f
    // Smootherstep has zero slope and acceleration at each turn.
    val eased = rise * rise * rise * (rise * (rise * 6f - 15f) + 10f)
    return eased * 2f - 1f
}

/** Observes platform changes; the renderer reads animation state only during draw. */
@Composable
internal fun rememberBackgroundMotionAllowed(motionEnabled: Boolean): Boolean {
    val context = LocalContext.current
    val view = LocalView.current
    val owner = remember(context) { context.findLifecycleOwner() }
    var conditions by remember(context, view, owner) {
        mutableStateOf(readConditions(context, view, owner))
    }

    DisposableEffect(context, view, owner) {
        val appContext = context.applicationContext
        val resolver = context.contentResolver
        val lifecycle = owner?.lifecycle
        var observerTree: ViewTreeObserver? = null

        fun refresh() {
            conditions = readConditions(context, view, owner)
        }

        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { refresh() }
        fun removeFocusListener() {
            observerTree?.let { if (it.isAlive) it.removeOnWindowFocusChangeListener(focusListener) }
            observerTree = null
        }
        fun addFocusListener() {
            removeFocusListener()
            val tree = view.viewTreeObserver
            if (tree.isAlive) {
                tree.addOnWindowFocusChangeListener(focusListener)
                observerTree = tree
            }
        }

        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                addFocusListener()
                refresh()
            }
            override fun onViewDetachedFromWindow(v: View) {
                removeFocusListener()
                refresh()
            }
        }
        view.addOnAttachStateChangeListener(attachListener)
        if (view.isAttachedToWindow) addFocusListener()

        val lifecycleObserver = LifecycleEventObserver { _, _ -> refresh() }
        lifecycle?.addObserver(lifecycleObserver)

        val powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context, intent: Intent) {
                refresh()
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            powerReceiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val animatorObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refresh()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            animatorObserver
        )
        refresh()

        onDispose {
            resolver.unregisterContentObserver(animatorObserver)
            appContext.unregisterReceiver(powerReceiver)
            lifecycle?.removeObserver(lifecycleObserver)
            view.removeOnAttachStateChangeListener(attachListener)
            removeFocusListener()
        }
    }

    return conditions.allowsMotion(motionEnabled)
}

private fun readConditions(context: Context, view: View, owner: LifecycleOwner?) =
    BackgroundMotionConditions(
        resumed = owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true,
        windowFocused = view.hasWindowFocus(),
        attached = view.isAttachedToWindow,
        powerSave = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true,
        animatorScale = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (_: SecurityException) {
            1f
        }
    )

private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var current: Context? = this
    while (current != null) {
        if (current is LifecycleOwner) return current
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}
