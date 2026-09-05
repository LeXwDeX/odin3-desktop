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
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val locked = isLocked
        val toggle = {
            VideoShaderEngine.toggleCurrentAppShader(this) {
                updateTileState()
            }
        }
        if (locked) unlockAndRun { toggle() } else toggle()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val runtime = VideoShaderEngine.state.value
        // Never make an unverified overlay look like confirmed game rendering.
        tile.state = Tile.STATE_INACTIVE
        tile.label = this.getString(R.string.text_game_filter)
        tile.subtitle = getString(when (runtime.status) {
            com.odin.desktop.shader.runtime.ShaderStatus.OVERLAY_UNCONFIRMED -> R.string.shader_tile_unconfirmed
            com.odin.desktop.shader.runtime.ShaderStatus.PREVIEW_ONLY -> R.string.shader_tile_preview
            com.odin.desktop.shader.runtime.ShaderStatus.PAUSED -> R.string.shader_tile_paused
            com.odin.desktop.shader.runtime.ShaderStatus.UNKNOWN -> R.string.shader_tile_unknown
            else -> runtime.status.message
        })
        tile.contentDescription = "${tile.label}: ${tile.subtitle}"
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
