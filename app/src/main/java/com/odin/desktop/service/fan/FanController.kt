package com.odin.desktop.service.fan

import android.content.Context
import android.provider.Settings
import android.util.Log

object FanController {
    private const val TAG = "FanController"

    const val SETTING_KEY_FAN_MODE = "fan_mode"
    const val SETTING_KEY_FAN_SPEED = "fan_speed"

    const val FAN_MODE_OFF = 0
    const val FAN_MODE_QUIET = 1
    const val FAN_MODE_SMART = 4
    const val FAN_MODE_SPORT = 5
    const val FAN_MODE_CUSTOM = 6

    fun getFanMode(context: Context): Int {
        return try {
            Settings.System.getInt(context.contentResolver, SETTING_KEY_FAN_MODE, FAN_MODE_SMART)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read fan_mode", e)
            FAN_MODE_SMART
        }
    }

    fun setFanMode(context: Context, mode: Int): Boolean {
        return HardwareController.setFanMode(context, mode)
    }
}
