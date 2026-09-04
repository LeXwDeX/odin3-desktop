package com.odin.desktop.service.afk

import com.odin.desktop.R
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
                        Toast.makeText(this, this.getString(R.string.text_allow_display_over_other_apps_before_starting), Toast.LENGTH_LONG).show()
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")))
                    }
                    else -> startForegroundService(Intent(this, AfkOverlayService::class.java))
                }
            }
        } catch (error: RuntimeException) {
            android.util.Log.e("AfkActionActivity", "Could not change AFK state", error)
            Toast.makeText(this, this.getString(R.string.text_cannot_toggle_the_idle_screen_check_overlay), Toast.LENGTH_LONG).show()
        } finally {
            finish()
        }
    }
}
