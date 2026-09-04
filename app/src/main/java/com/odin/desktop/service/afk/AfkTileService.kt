package com.odin.desktop.service.afk

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.odin.desktop.R

class AfkTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        requestRefresh(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { launchAction() }
        } else {
            launchAction()
        }
    }

    private fun launchAction() {
        val intent = Intent(this, AfkActionActivity::class.java).apply {
            // A separate temporary task returns to the game when the action finishes.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (error: RuntimeException) {
            android.util.Log.e("AfkTileService", "Could not launch AFK action", error)
            Toast.makeText(this, "无法打开息屏挂机，请重试", Toast.LENGTH_LONG).show()
            updateTileState()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = AfkOverlayService.isAfkRunning

        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_afk_label)
        tile.subtitle = if (isRunning) getString(R.string.tile_afk_active) else getString(R.string.tile_afk_inactive)
        tile.updateTile()
    }

    companion object {
        fun requestRefresh(context: Context) {
            try {
                TileService.requestListeningState(context, ComponentName(context, AfkTileService::class.java))
            } catch (error: RuntimeException) {
                android.util.Log.w("AfkTileService", "Could not request tile refresh", error)
            }
        }
    }
}
