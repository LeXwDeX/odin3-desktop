package com.odin.desktop.hardware

import android.app.Instrumentation
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.view.ViewTreeObserver
import com.odin.desktop.ui.MainActivity
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Fault injection replaces ONLY this debug app process's ServiceManager cache entry. */
class StartupProbeInstrumentation : Instrumentation() {
    private var fault = "read"
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        fault = arguments?.getString("fault") ?: "read"
        require(fault in setOf("read", "offline", "timeout", "malformed"))
        start()
    }

    override fun onStart() {
        val report = JSONObject().put("fault", fault)
        var cache: MutableMap<String, IBinder>? = null
        var original: IBinder? = null
        var activity: MainActivity? = null
        try {
            if (fault != "read") {
                val field = Class.forName("android.os.ServiceManager").getDeclaredField("sCache")
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val localCache = field.get(null) as MutableMap<String, IBinder>
                cache = localCache
                original = localCache["PServerBinder"]
                localCache["PServerBinder"] = object : Binder() {
                    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                        when (fault) {
                            "offline" -> throw android.os.DeadObjectException()
                            "timeout" -> {
                                SystemClock.sleep(2_500)
                                reply?.writeByteArray("ODIN:124 timeout".toByteArray())
                            }
                            else -> reply?.writeByteArray("invalid response".toByteArray())
                        }
                        return true
                    }
                }
            }
            val started = SystemClock.uptimeMillis()
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
            val frame = CountDownLatch(1)
            runOnMainSync {
                val view = activity!!.window.decorView
                view.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
                    override fun onDraw() {
                        frame.countDown()
                        view.post { view.viewTreeObserver.removeOnDrawListener(this) }
                    }
                })
                view.invalidate()
            }
            check(frame.await(5, TimeUnit.SECONDS)) { "No launcher frame" }
            report.put("draw_ms", SystemClock.uptimeMillis() - started)
            val heartbeat = CountDownLatch(1)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                activity!!.onBackPressedDispatcher.onBackPressed()
                heartbeat.countDown()
            }
            check(heartbeat.await(1, TimeUnit.SECONDS)) { "Main thread blocked" }
            report.put("back_and_main_thread_responsive", true)
            if (fault != "read") {
                // Wait for at least one injected read to finish. No real firmware command executes.
                SystemClock.sleep(if (fault == "timeout") 5_500 else 1_000)
                val health = CountDownLatch(1)
                android.os.Handler(android.os.Looper.getMainLooper()).post { health.countDown() }
                check(health.await(1, TimeUnit.SECONDS)) { "Main thread blocked after hardware failure" }
                report.put("responsive_after_fault", true)
            }
        } catch (failure: Throwable) { report.put("error", failure.toString()) }
        finally {
            cache?.let { if (original == null) it.remove("PServerBinder") else it["PServerBinder"] = original!! }
            report.put("local_cache_restored", true)
        }
        finish(if (report.has("error")) 1 else 0, Bundle().apply { putString("report", report.toString()) })
    }
}
