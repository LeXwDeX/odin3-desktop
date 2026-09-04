package com.odin.desktop.service.afk

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

/** A short, transparent user action that lets Quick Settings collapse before showing the mask. */
class AfkActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (savedInstanceState == null) {
                when {
                    AfkOverlayService.isAfkRunning -> stopService(Intent(this, AfkOverlayService::class.java))
                    !Settings.canDrawOverlays(this) -> {
                        Toast.makeText(this, "请先授予悬浮窗权限，再点击息屏挂机", Toast.LENGTH_LONG).show()
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")))
                    }
                    else -> startForegroundService(Intent(this, AfkOverlayService::class.java))
                }
            }
        } catch (error: RuntimeException) {
            android.util.Log.e("AfkActionActivity", "Could not change AFK state", error)
            Toast.makeText(this, "无法切换息屏挂机，请检查悬浮窗权限", Toast.LENGTH_LONG).show()
        } finally {
            finish()
        }
    }
}
