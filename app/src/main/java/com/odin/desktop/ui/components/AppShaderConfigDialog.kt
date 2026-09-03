package com.odin.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.odin.desktop.data.model.InstalledApp
import com.odin.desktop.shader.model.AppShaderConfigEntity
import com.odin.desktop.ui.components.base.ConsoleModalDialog
import com.odin.desktop.ui.theme.CardBackground
import com.odin.desktop.ui.theme.CardBorder
import com.odin.desktop.ui.theme.CyanAccent
import com.odin.desktop.ui.theme.DarkSurface
import com.odin.desktop.ui.theme.GreenActive
import com.odin.desktop.ui.theme.OrangeWarning
import com.odin.desktop.ui.theme.TextDim
import com.odin.desktop.ui.theme.TextWhite

data class ShaderSettingItem(
    val id: String,
    val title: String,
    val valueLabel: String,
    val valueColor: Color,
    val description: String
)

@Composable
fun AppShaderConfigDialog(
    isOpen: Boolean,
    app: InstalledApp?,
    config: AppShaderConfigEntity?,
    focusIndex: Int,
    onDismiss: () -> Unit,
    onToggleEnable: () -> Unit,
    onPreviewShader: () -> Unit,
    onSelectItem: (Int) -> Unit
) {
    if (!isOpen || app == null) return

    val currentConfig = config ?: AppShaderConfigEntity.defaultFor(app.packageName)

    val items = listOf(
        ShaderSettingItem(
            id = "shader_type",
            title = "1. 选择 Shader 类型",
            valueLabel = if (currentConfig.isEnabled) "GameNative CRT 扫描线 (已开启)" else "无 (已关闭)",
            valueColor = if (currentConfig.isEnabled) GreenActive else TextDim,
            description = "进入该应用时自动激活全屏 Shader 滤镜 (离开即自动恢复)"
        ),
        ShaderSettingItem(
            id = "preview_shader",
            title = "2. 预览 Shader (预留)",
            valueLabel = "待接入截图",
            valueColor = OrangeWarning,
            description = "【功能预留】等待导入游戏画面截图后，将开启实时参数调优"
        )
    )

    ConsoleModalDialog(
        isOpen = isOpen,
        onDismissRequest = onDismiss,
        title = "专属 VideoShader 滤镜设置",
        footerHint = "【上下键选择 • A 键切换/执行 • B 键保存返回】",
        maxWidth = 620.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 目标应用信息条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground, RoundedCornerShape(8.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                app.icon?.let { drawable ->
                    androidx.compose.foundation.Image(
                        bitmap = drawable.toBitmap(64, 64).asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.size(10.dp))
                Column {
                    Text(
                        text = app.label,
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${app.packageName} • 仅在该应用前台时生效",
                        color = TextDim,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 设置项列表 (1. 选择 Shader 类型  2. 预览 Shader)
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(items) { index, item ->
                    val isFocused = index == focusIndex
                    val bg = if (isFocused) CyanAccent.copy(alpha = 0.18f) else DarkSurface
                    val border = if (isFocused) CyanAccent else Color.Transparent

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .border(1.5.dp, border, RoundedCornerShape(8.dp))
                            .clickable {
                                onSelectItem(index)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = if (isFocused) CyanAccent else TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = item.description,
                                    color = TextDim,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = item.valueLabel,
                                color = item.valueColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
