package com.odin.desktop.shader.overlay

import com.odin.desktop.locale.AppLanguageContext
import com.odin.desktop.R
import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.odin.desktop.shader.engine.VideoShaderEngine

class ShaderTileService : TileService() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(AppLanguageContext.wrap(base))
    }

    override fun onTileAdded() {
        super.onTileAdded()
        requestRefresh(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState(VideoShaderEngine.isShaderActive())
    }

    override fun onClick() {
        super.onClick()
        val locked = isLocked
        val toggle = {
            VideoShaderEngine.toggleCurrentAppShader(this) {
                updateTileState(it)
            }
        }
        if (locked) unlockAndRun { toggle() } else toggle()
    }

    private fun updateTileState(isActive: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = this.getString(R.string.text_game_filter)
        tile.subtitle = if (isActive) this.getString(R.string.text_on_hold_to_adjust) else this.getString(R.string.text_off_hold_to_adjust)
        tile.updateTile()
    }

    companion object {
        fun requestRefresh(context: Context) {
            val appContext = context.applicationContext
            runCatching {
                requestListeningState(appContext, ComponentName(appContext, ShaderTileService::class.java))
            }.onFailure {
                Log.w("ShaderTileService", "Could not request shader tile refresh", it)
            }
        }
    }
}
