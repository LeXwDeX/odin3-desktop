package com.odin.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import com.odin.desktop.R
import com.odin.desktop.service.fan.HardwareController
import com.odin.desktop.ui.components.base.*
import com.odin.desktop.ui.navigation.FocusZone
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.theme.OdinSizes
import com.odin.desktop.ui.theme.OdinInsets
import com.odin.desktop.ui.theme.OdinSpacing
import com.odin.desktop.ui.theme.OdinTypography

@Composable
fun BottomDockBar(
    performanceMode: Int,
    fanMode: Int,
    joystickLightEnabled: Boolean,
    chargingSeparation: Boolean,
    chargePowerLimit: Boolean,
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

    // 2. 原厂风扇档位：关闭 / 智能 / 最高；不附加应用自动策略。
    val (fanLabel, fanColor) = when (fanMode) {
        HardwareController.FAN_OFF -> strings.getString(R.string.text_off) to palette.textDim
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

    // Compact labels are display-only resources; accessibility retains each full state.
    val compactTitles = listOf(R.string.dock_performance_title, R.string.dock_fan_title,
        R.string.dock_lights_title, R.string.dock_charging_title, R.string.dock_airplane_title)
    val compactValues = listOf(
        strings.getString(when (performanceMode) {
            HardwareController.PERF_NORMAL -> R.string.dock_performance_normal
            HardwareController.PERF_PERFORMANCE -> R.string.dock_performance_medium
            HardwareController.PERF_HIGH_PERFORMANCE -> R.string.dock_performance_maximum
            else -> R.string.dock_unknown
        }),
        strings.getString(when (fanMode) {
            HardwareController.FAN_OFF -> R.string.dock_off
            HardwareController.FAN_SMART -> R.string.dock_fan_smart
            HardwareController.FAN_SPORT -> R.string.dock_fan_maximum
            HardwareController.FAN_QUIET -> R.string.dock_fan_quiet
            2, 3, 6 -> R.string.dock_fan_system
            else -> R.string.dock_unknown
        }),
        strings.getString(if (joystickLightEnabled) R.string.dock_on else R.string.dock_off),
        if (chargingSeparation) strings.getString(if (chargePowerLimit) R.string.dock_bypass_5v else R.string.dock_bypass_9v)
            else chargeLabel,
        strings.getString(if (airplaneMode) R.string.dock_on else R.string.dock_off)
    )

    val titles = compactTitles.map(strings::getString)
    val icons = listOf("⚡", "🌀", "💡", "🔋", "✈")
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth().height(OdinSizes.chromeHeight())
        .padding(horizontal = OdinSpacing.page, vertical = OdinSpacing.sm)) {
        val columnWidth = (maxWidth - OdinSpacing.sm * (dockItems.size - 1)) / dockItems.size
        val insets = OdinInsets.control.calculateLeftPadding(direction) + OdinInsets.control.calculateRightPadding(direction) +
            OdinInsets.badge.calculateLeftPadding(direction) + OdinInsets.badge.calculateRightPadding(direction)
        // Keep every name and state legible. Decorative icons are removed from the whole row together.
        val showIcons = titles.indices.all { index ->
            val textWidth = textMeasurer.measure(titles[index], OdinTypography.body, softWrap = false).size.width +
                textMeasurer.measure(compactValues[index], OdinTypography.caption, softWrap = false).size.width
            textWidth + with(density) { (insets + OdinSizes.icon + OdinSpacing.sm * 2).toPx() } <=
                with(density) { columnWidth.toPx() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OdinSpacing.sm)) {
            dockItems.forEachIndexed { index, item ->
                OdinControl(text = titles[index],
                    onClick = { onItemClick(index) }, focused = index == selectedDockIndex && focusZone == FocusZone.DOCK,
                    badge = compactValues[index], badgeRole = when (item.stateColor) {
                        palette.active -> BadgeRole.ACTIVE
                        palette.warning -> BadgeRole.WARNING
                        palette.danger -> BadgeRole.DANGER
                        palette.textDim -> BadgeRole.NEUTRAL
                        else -> BadgeRole.INFO
                    },
                    icon = if (showIcons) {
                        { OdinEmojiIcon(icons[index]) }
                    } else null,
                    accessibilityLabel = item.title + ": " + item.value,
                    modifier = Modifier.weight(1f).fillMaxHeight(), maxLines = 1)
            }
        }
    }
}

private data class DockItemData(val title: String, val value: String, val stateColor: Color)
