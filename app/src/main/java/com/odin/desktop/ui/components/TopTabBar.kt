package com.odin.desktop.ui.components

import com.odin.desktop.ui.theme.OdinCorners
import com.odin.desktop.ui.theme.OdinSizes
import com.odin.desktop.ui.theme.OdinInsets
import com.odin.desktop.ui.theme.OdinSpacing
import com.odin.desktop.ui.theme.OdinTypography
import com.odin.desktop.ui.theme.LocalOdinPalette
import androidx.compose.ui.platform.LocalContext
import com.odin.desktop.data.model.displayName
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import com.odin.desktop.R
import com.odin.desktop.ui.viewmodel.LauncherTelemetry
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.odin.desktop.data.entity.TabEntity
import com.odin.desktop.ui.navigation.FocusZone

@Composable
fun TopTabBar(
    telemetry: LauncherTelemetry,
    tabs: List<TabEntity>,
    selectedTabIndex: Int,
    isDashboardSelected: Boolean,
    onDashboardSelected: () -> Unit,
    isConfigFocused: Boolean,
    focusZone: FocusZone,
    onTabSelected: (Int) -> Unit,
    onConfigClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(OdinSizes.headerHeight)
            .padding(horizontal = OdinSpacing.page, vertical = OdinSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ShoulderButtonBadge(label = "L1")
        val listState = rememberLazyListState()
        val activeIndex = if (isDashboardSelected) 0 else selectedTabIndex + 1
        LaunchedEffect(activeIndex) { listState.animateScrollToItem(activeIndex) }
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = OdinSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OdinSpacing.xs)
        ) {
            item(key = "dashboard") {
                HomeTab(strings.getString(com.odin.desktop.R.string.page_dashboard), isDashboardSelected && !isConfigFocused, focusZone, onDashboardSelected)
            }
            itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
                HomeTab(tab.displayName(strings), !isDashboardSelected && selectedTabIndex == index && !isConfigFocused,
                    focusZone) { onTabSelected(index) }
            }
        }
        ShoulderButtonBadge(label = "R1")
        Spacer(Modifier.padding(horizontal = OdinSpacing.sm))

        HeaderTelemetry(telemetry)
        Spacer(Modifier.width(12.dp))

        // 右侧固定 [CONFIG] 设置按钮
        val isConfigSelected = isConfigFocused && focusZone == FocusZone.TABS
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(OdinCorners.control))
                .background(if (isConfigSelected) palette.selection else palette.surface)
                .border(
                    width = 1.dp,
                    color = if (isConfigSelected) palette.accent else Color.Transparent,
                    shape = RoundedCornerShape(OdinCorners.control)
                )
                .clickable { onConfigClick() }
                .padding(OdinInsets.compactButton)
        ) {
            Text(
                text = strings.getString(com.odin.desktop.R.string.page_config),
                color = if (isConfigSelected) palette.accent else palette.text,
                style = OdinTypography.h3,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HomeTab(label: String, selected: Boolean, focusZone: FocusZone, onClick: () -> Unit) {
    val palette = LocalOdinPalette.current
    val focused = selected && focusZone == FocusZone.TABS
    Box(
        Modifier.widthIn(max = 150.dp).clip(RoundedCornerShape(OdinCorners.control))
            .background(if (focused) palette.selection else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(OdinInsets.tab)
    ) {
        Text(label, color = if (selected) palette.accent else palette.textDim, style = OdinTypography.h3,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ShoulderButtonBadge(label: String) {
    val palette = LocalOdinPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(OdinCorners.badge))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(OdinCorners.badge))
            .padding(OdinInsets.badge)
    ) {
        Text(
            text = label,
            color = palette.textDim,
            style = OdinTypography.caption,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HeaderTelemetry(telemetry: LauncherTelemetry) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    Row(Modifier.width(208.dp), horizontalArrangement = Arrangement.spacedBy(OdinSpacing.md),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(84.dp)) {
            Text(strings.getString(R.string.header_battery, telemetry.battery.percent?.toString() ?: "—"),
                color = palette.text, style = OdinTypography.dataLabel, maxLines = 1)
            Text(strings.getString(when (telemetry.battery.status) {
                android.os.BatteryManager.BATTERY_STATUS_CHARGING -> R.string.header_charging
                android.os.BatteryManager.BATTERY_STATUS_FULL -> R.string.header_full
                android.os.BatteryManager.BATTERY_STATUS_DISCHARGING,
                android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> R.string.header_on_battery
                else -> R.string.header_unread
            }), color = palette.textDim, style = OdinTypography.dataNote, maxLines = 1)
        }
        Column(Modifier.weight(1f)) {
            Text(strings.getString(R.string.header_fan, telemetry.fan?.rpm?.toString() ?: "—"), color = palette.text,
                fontFamily = FontFamily.Monospace, style = OdinTypography.dataLabel, maxLines = 1)
            Text("PWM ${telemetry.fan?.dutyPercent ?: "—"}%", color = palette.textDim,
                fontFamily = FontFamily.Monospace, style = OdinTypography.dataNote, maxLines = 1)
        }
    }
}
