package com.odin.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    chargePowerLimit: Boolean,
    autoFanControlEnabled: Boolean,
    airplaneMode: Boolean,
    selectedDockIndex: Int,
    focusZone: FocusZone,
    onItemClick: (Int) -> Unit,
    onToggleChargingFanMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val perfLabel = when (performanceMode) {
        HardwareController.PERF_PERFORMANCE -> "性能"
        HardwareController.PERF_HIGH_PERFORMANCE -> "高性能"
        HardwareController.PERF_NORMAL -> "默认"
        else -> "未连接"
    }
    val fanLabel = when (fanMode) {
        HardwareController.FAN_OFF -> "关闭"
        HardwareController.FAN_QUIET -> "静音"
        HardwareController.FAN_SPORT -> "高性能"
        HardwareController.FAN_SMART -> "智能"
        else -> "未知"
    }
    val dockItems = listOf(
        DockItemData("性能", perfLabel, "A 切换档位",
            if (performanceMode == HardwareController.PERF_HIGH_PERFORMANCE) RedDanger
            else if (performanceMode == HardwareController.PERF_PERFORMANCE) OrangeWarning else GreenActive),
        DockItemData("风扇", fanLabel, "X 充电模式 ${if (autoFanControlEnabled) "开" else "关"}",
            if (fanMode == HardwareController.FAN_OFF) TextDim else CyanAccent),
        DockItemData("摇杆灯", if (joystickLightEnabled) "开启" else "关闭", "A 开关",
            if (joystickLightEnabled) CyanAccent else TextDim),
        DockItemData("充电限制", if (chargeLimit80) "80% 开" else "80% 关",
            "5V 档 ${if (chargePowerLimit) "开" else "关"}", if (chargeLimit80) GreenActive else TextDim),
        DockItemData("飞行模式", if (airplaneMode) "开" else "关", "A 开关",
            if (airplaneMode) OrangeWarning else TextDim)
    )

    ProvideTextStyle(TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))) {
        Row(
            modifier = modifier.fillMaxWidth().height(58.dp).padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dockItems.forEachIndexed { index, item ->
                val isFocused = index == selectedDockIndex && focusZone == FocusZone.DOCK
                val borderColor by animateColorAsState(
                    if (isFocused) CyanAccent else Color(0xFF292929), label = "dock_border"
                )
                val bgColor by animateColorAsState(
                    if (isFocused) CyanAccent.copy(alpha = 0.16f) else DarkSurface, label = "dock_bg"
                )
                Column(
                    Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(8.dp))
                        .background(bgColor).border(if (isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
                        .focusProperties { canFocus = false }
                        .clickable(role = Role.Button) { onItemClick(index) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.title, color = TextWhite, fontSize = 12.sp, lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(item.value, color = item.stateColor, fontSize = 12.sp, lineHeight = 16.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val subtitleModifier = if (index == 1) Modifier.fillMaxWidth()
                        .focusProperties { canFocus = false }
                        .clickable(role = Role.Switch, onClick = onToggleChargingFanMode)
                    else Modifier
                    Text(item.subtitle, color = if (index == 1 && autoFanControlEnabled) CyanAccent else TextDim,
                        fontSize = 10.sp, lineHeight = 13.sp,
                        modifier = subtitleModifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private data class DockItemData(val title: String, val value: String, val subtitle: String, val stateColor: Color)
