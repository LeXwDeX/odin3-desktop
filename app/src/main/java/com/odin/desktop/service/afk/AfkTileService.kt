package com.odin.desktop.service.afk

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.odin.desktop.R

class AfkTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限以使用息屏挂机", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivityAndCollapse(intent)
            return
        }

        if (AfkOverlayService.isAfkRunning) {
            // 正在运行 -> 停止挂机
            val stopIntent = Intent(this, AfkOverlayService::class.java).apply {
                action = AfkOverlayService.ACTION_STOP_AFK
            }
            startService(stopIntent)
        } else {
            // 未运行 -> 启动挂机
            val startIntent = Intent(this, AfkOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(startIntent)
            } else {
                startService(startIntent)
            }
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = AfkOverlayService.isAfkRunning

        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isRunning) getString(R.string.tile_afk_active) else getString(R.string.tile_afk_inactive)
        tile.updateTile()
    }
}
