package com.odin.desktop.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.odin.desktop.service.fan.FanWatchdogService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // 1. 启动温控与充电风扇守护服务
            try {
                val fanIntent = Intent(context, FanWatchdogService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(fanIntent)
                } else {
                    context.startService(fanIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // The system starts the user-selected HOME. Never launch an Activity here.
        }
    }
}
