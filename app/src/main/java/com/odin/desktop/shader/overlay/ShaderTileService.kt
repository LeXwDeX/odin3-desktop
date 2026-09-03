package com.odin.desktop.shader.overlay

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.odin.desktop.R
import com.odin.desktop.shader.engine.VideoShaderEngine

class ShaderTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState(VideoShaderEngine.isShaderActive())
    }

    override fun onClick() {
        super.onClick()
        VideoShaderEngine.toggleCurrentAppShader(this) { isEnabled ->
            updateTileState(isEnabled)
        }
    }

    private fun updateTileState(isActive: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isActive) "Shader: 开启" else "Shader: 关闭"
        tile.subtitle = if (isActive) "滤镜渲染中" else "未加载"
        tile.updateTile()
    }
}
