package com.odin.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odin.desktop.service.fan.HardwareController
import com.odin.desktop.ui.navigation.FocusZone
import com.odin.desktop.ui.theme.CyanAccent
import com.odin.desktop.ui.theme.DarkSurface
import com.odin.desktop.ui.theme.GreenActive
import com.odin.desktop.ui.theme.OrangeWarning
import com.odin.desktop.ui.theme.RedDanger
import com.odin.desktop.ui.theme.TextDim
import com.odin.desktop.ui.theme.TextWhite

@Composable
fun BottomDockBar(
    performanceMode: Int,
    fanMode: Int,
    joystickLightEnabled: Boolean,
    chargeLimit80: Boolean,
    airplaneMode: Boolean,
    selectedDockIndex: Int,
    focusZone: FocusZone,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. CPU 性能：正常 -> 性能 -> 高性能
    val perfLabel = when (performanceMode) {
        HardwareController.PERF_PERFORMANCE -> "性能"
        HardwareController.PERF_HIGH_PERFORMANCE -> "高性能"
        else -> "正常"
    }

    // 2. 风扇：关闭 -> 静音 -> 智能 -> 极速
    val fanLabel = when (fanMode) {
        HardwareController.FAN_OFF -> "关闭"
        HardwareController.FAN_QUIET -> "静音"
        HardwareController.FAN_SPORT -> "极速"
        else -> "智能"
    }

    val dockItems = listOf(
        DockItemData("⚡ 性能", perfLabel, if (performanceMode == HardwareController.PERF_HIGH_PERFORMANCE) RedDanger else if (performanceMode == HardwareController.PERF_PERFORMANCE) OrangeWarning else GreenActive),
        DockItemData("🌀 风扇", fanLabel, if (fanMode == HardwareController.FAN_OFF) RedDanger else if (fanMode == HardwareController.FAN_SPORT) OrangeWarning else GreenActive),
        DockItemData("💡 摇杆灯", if (joystickLightEnabled) "开启" else "关闭", if (joystickLightEnabled) CyanAccent else TextDim),
        DockItemData("🔋 充电限制", if (chargeLimit80) "80%" else "关", if (chargeLimit80) GreenActive else TextDim),
        DockItemData("✈️ 飞行模式", if (airplaneMode) "开" else "关", if (airplaneMode) OrangeWarning else TextDim)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dockItems.forEachIndexed { index, item ->
            val isFocused = index == selectedDockIndex && focusZone == FocusZone.DOCK

            val borderColor by animateColorAsState(
                targetValue = if (isFocused) CyanAccent else Color.Transparent,
                label = "dock_border"
            )
            val bgColor by animateColorAsState(
                targetValue = if (isFocused) CyanAccent.copy(alpha = 0.20f) else DarkSurface,
                label = "dock_bg"
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
                    .clickable { onItemClick(index) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.title,
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = ": ${item.value}",
                        color = item.stateColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private data class DockItemData(
    val title: String,
    val value: String,
    val stateColor: Color
)
