package com.odin.desktop.ui.components

import com.odin.desktop.ui.theme.LocalOdinPalette
import androidx.compose.ui.platform.LocalContext
import com.odin.desktop.R
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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

@Composable
fun BottomDockBar(
    performanceMode: Int,
    fanMode: Int,
    joystickLightEnabled: Boolean,
    chargingSeparation: Boolean,
    chargePowerLimit: Boolean,
    autoFanControlEnabled: Boolean,
    airplaneMode: Boolean,
    selectedDockIndex: Int,
    focusZone: FocusZone,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    // 1. 性能：默认/性能/最高 【绿色/黄色/红色】按 A 循环 (安全/警告/严重)
    val (perfLabel, perfColor) = when (performanceMode) {
        HardwareController.PERF_NORMAL -> strings.getString(R.string.text_normal) to palette.active
        HardwareController.PERF_PERFORMANCE -> strings.getString(R.string.text_performance) to palette.warning
        HardwareController.PERF_HIGH_PERFORMANCE -> strings.getString(R.string.text_maximum) to palette.danger
        else -> strings.getString(R.string.text_offline) to palette.textDim
    }

    // 2. 风扇：
    // - 充电风扇静音模式下停转: 蓝色 "关闭" (特殊状态：充电静音策略生效，风扇安全停转)
    // - 手动常规档位 / 运转状态:
    //   - 关闭: 灰色 "关闭" (灰色 关闭/OFF)
    //   - 智能: 绿色 "智能" (绿色 安全/恒温)
    //   - 最大: 红色 "最大" (红色 严重/极速满负荷)
    val (fanLabel, fanColor) = when (fanMode) {
        HardwareController.FAN_OFF -> if (autoFanControlEnabled) strings.getString(R.string.text_off) to palette.special else strings.getString(R.string.text_off) to palette.textDim
        HardwareController.FAN_SMART -> strings.getString(R.string.text_smart) to palette.active
        HardwareController.FAN_SPORT -> strings.getString(R.string.text_maximum_2) to palette.danger
        HardwareController.FAN_QUIET -> strings.getString(R.string.text_quiet) to palette.active
        2, 3, 6 -> strings.getString(R.string.text_system) to palette.textDim
        else -> strings.getString(R.string.text_unknown) to palette.textDim
    }

    // 3. 摇杆灯：开启 (绿色 - ON 是绿色) / 关闭 (灰色 - OFF 是灰色)
    val lightLabel = if (joystickLightEnabled) strings.getString(R.string.text_on_2) else strings.getString(R.string.text_off)
    val lightColor = if (joystickLightEnabled) palette.active else palette.textDim

    // 4. 充电优化：按 X 启动充电分离 (红色 5V分离/9V分离 - 严重)；按 A 切换 5V 3A (绿色 - 安全) / 9V 3A (黄色 - 警告)
    val (chargeLabel, chargeColor) = if (chargingSeparation) {
        (if (chargePowerLimit) strings.getString(R.string.text_5v_bypass) else strings.getString(R.string.text_9v_bypass)) to palette.danger
    } else {
        if (chargePowerLimit) "5V 3A" to palette.active else "9V 3A" to palette.warning
    }

    // 5. 飞行模式：开启 (绿色 - ON 是绿色) / 关闭 (灰色 - OFF 是灰色)
    val airplaneLabel = if (airplaneMode) strings.getString(R.string.text_on_2) else strings.getString(R.string.text_off)
    val airplaneColor = if (airplaneMode) palette.active else palette.textDim

    val dockItems = listOf(
        DockItemData(strings.getString(R.string.text_performance_2), perfLabel, perfColor),
        DockItemData(strings.getString(R.string.text_fan), fanLabel, fanColor),
        DockItemData(strings.getString(R.string.text_stick_lights), lightLabel, lightColor),
        DockItemData(strings.getString(R.string.text_charging), chargeLabel, chargeColor),
        DockItemData(strings.getString(R.string.text_airplane_mode), airplaneLabel, airplaneColor)
    )

    ProvideTextStyle(TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dockItems.forEachIndexed { index, item ->
                val isFocused = index == selectedDockIndex && focusZone == FocusZone.DOCK
                val borderColor by animateColorAsState(
                    if (isFocused) palette.accent else Color(0xFF292929), label = "dock_border"
                )
                val bgColor by animateColorAsState(
                    if (isFocused) palette.accent.copy(alpha = 0.16f) else palette.surface, label = "dock_bg"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(bgColor)
                        .border(if (isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
                        .focusProperties { canFocus = false }
                        .clickable(role = Role.Button) { onItemClick(index) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = item.title,
                            color = palette.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = ": ${item.value}",
                            color = item.stateColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private data class DockItemData(val title: String, val value: String, val stateColor: Color)
