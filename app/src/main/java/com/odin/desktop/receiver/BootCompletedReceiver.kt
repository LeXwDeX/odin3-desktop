package com.odin.desktop.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.odin.desktop.service.fan.FanWatchdogService
import com.odin.desktop.service.fan.HardwareController
import com.odin.desktop.ui.MainActivity

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
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

            // 2. 开机自动拉起 Odin 启动台 (若开启了开机自启)
            val autoStart = HardwareController.isBootAutoStartEnabled(context)
            if (autoStart) {
                try {
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    }
                    context.startActivity(launchIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
