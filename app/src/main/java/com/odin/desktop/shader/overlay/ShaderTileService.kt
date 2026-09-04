package com.odin.desktop.shader.overlay

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.odin.desktop.shader.engine.VideoShaderEngine

class ShaderTileService : TileService() {
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
        tile.label = "滤镜开关"
        tile.subtitle = if (isActive) "已开启 · 长按调整" else "已关闭 · 长按调整"
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
