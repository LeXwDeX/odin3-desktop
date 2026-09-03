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
    onToggleDynamic: () -> Unit,
    onCycleIntensity: () -> Unit,
    onCyclePhosphor: () -> Unit,
    onToggleVignette: () -> Unit
) {
    if (!isOpen || app == null) return

    val currentConfig = config ?: AppShaderConfigEntity.defaultFor(app.packageName)

    val intensityLabel = when {
        currentConfig.scanlineIntensity <= 0.35f -> "弱 (30%)"
        currentConfig.scanlineIntensity <= 0.60f -> "中 (50%)"
        else -> "强 (75%)"
    }

    val phosphorLabel = when {
        currentConfig.phosphorIntensity <= 0.05f -> "关"
        currentConfig.phosphorIntensity <= 0.25f -> "弱 (20%)"
        else -> "强 (40%)"
    }

    val items = listOf(
        ShaderSettingItem(
            id = "enable",
            title = "🎮 滤镜生效状态",
            valueLabel = if (currentConfig.isEnabled) "已开启" else "已关闭",
            valueColor = if (currentConfig.isEnabled) GreenActive else TextDim,
            description = "进入该应用时是否自动激活全屏 VideoShader"
        ),
        ShaderSettingItem(
            id = "dynamic",
            title = "⚡ 扫描线运动形态",
            valueLabel = if (currentConfig.isDynamic) "动态滚动 (微波动画)" else "静态固定 (0 额外功耗)",
            valueColor = if (currentConfig.isDynamic) CyanAccent else TextWhite,
            description = "静态仅绘制一次即可持续呈现，动态扫描线具有平滑扫频动画"
        ),
        ShaderSettingItem(
            id = "intensity",
            title = "📐 扫描线浓度深度",
            valueLabel = intensityLabel,
            valueColor = CyanAccent,
            description = "控制水平阴影扫描光栅的明暗对比度"
        ),
        ShaderSettingItem(
            id = "phosphor",
            title = "📺 PVM 显像管 RGB 格子",
            valueLabel = phosphorLabel,
            valueColor = if (currentConfig.phosphorIntensity > 0.05f) OrangeWarning else TextDim,
            description = "模拟索尼特丽珑 RGB 三原色荧光点阵排布"
        ),
        ShaderSettingItem(
            id = "vignette",
            title = "🔘 CRT 弧面微暗角",
            valueLabel = if (currentConfig.vignetteIntensity > 0.1f) "开启" else "关闭",
            valueColor = if (currentConfig.vignetteIntensity > 0.1f) CyanAccent else TextDim,
            description = "边缘微弱暗角模拟球面老式监视器透镜效果"
        )
    )

    ConsoleModalDialog(
        isOpen = isOpen,
        onDismissRequest = onDismiss,
        title = "专属 VideoShader 滤镜设置",
        footerHint = "【上下键选择 • A 键切换 • B 键保存返回】",
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
                        text = app.packageName,
                        color = TextDim,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 设置项列表
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
                                when (index) {
                                    0 -> onToggleEnable()
                                    1 -> onToggleDynamic()
                                    2 -> onCycleIntensity()
                                    3 -> onCyclePhosphor()
                                    4 -> onToggleVignette()
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
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
