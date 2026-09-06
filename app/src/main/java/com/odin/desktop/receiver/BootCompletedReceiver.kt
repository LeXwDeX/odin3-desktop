package com.odin.desktop.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.odin.desktop.service.fan.FanWatchdogService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // Restore only the user's enabled automatic policy, never a manual-mode service.
            try {
                FanWatchdogService.sync(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // The system starts the user-selected HOME. Never launch an Activity here.
        }
    }
}
