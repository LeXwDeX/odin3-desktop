package com.odin.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.odin.desktop.R
import com.odin.desktop.data.entity.TabEntity
import com.odin.desktop.data.model.displayName
import com.odin.desktop.ui.components.base.*
import com.odin.desktop.ui.navigation.FocusZone
import com.odin.desktop.ui.theme.OdinSizes
import com.odin.desktop.ui.theme.OdinSpacing
import com.odin.desktop.ui.viewmodel.LauncherTelemetry
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

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
    val strings = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(OdinSizes.headerHeight())
            .padding(horizontal = OdinSpacing.page, vertical = OdinSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val listState = rememberLazyListState()
        val activeIndex = if (isDashboardSelected) 0 else selectedTabIndex + 1
        LaunchedEffect(activeIndex) { listState.animateScrollToItem(activeIndex) }
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f),
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
        Spacer(Modifier.width(OdinSpacing.md))

        HeaderTelemetry(telemetry)
        Spacer(Modifier.width(OdinSpacing.md))

        // 右侧固定 [CONFIG] 设置按钮
        val isConfigSelected = isConfigFocused && focusZone == FocusZone.TABS
        OdinControl(strings.getString(R.string.page_config), onClick = onConfigClick,
            focused = isConfigSelected, modifier = Modifier.widthIn(max = 132.dp).width(IntrinsicSize.Max), maxLines = 1)
    }
}

@Composable
private fun HomeTab(label: String, selected: Boolean, focusZone: FocusZone, onClick: () -> Unit) {
    OdinControl(label, onClick, selected = selected, focused = selected && focusZone == FocusZone.TABS,
        modifier = Modifier.widthIn(max = 150.dp).width(IntrinsicSize.Max), maxLines = 1)
}

@Composable
private fun HeaderTelemetry(telemetry: LauncherTelemetry) {
    val strings = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT) }
    var clock by remember { mutableStateOf(LocalTime.now().format(formatter)) }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                clock = LocalTime.now().format(formatter)
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
    }
    Row(Modifier.width(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(OdinSpacing.md)) {
        OdinStatusReadout(
            primary = strings.getString(R.string.header_battery, telemetry.battery.percent?.toString() ?: "—"),
            supporting = strings.getString(when (telemetry.battery.status) {
                android.os.BatteryManager.BATTERY_STATUS_CHARGING -> R.string.header_charging
                android.os.BatteryManager.BATTERY_STATUS_FULL -> R.string.header_full
                android.os.BatteryManager.BATTERY_STATUS_DISCHARGING,
                android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> R.string.header_on_battery
                else -> R.string.header_unread
            }),
            modifier = Modifier.alignBy(FirstBaseline)
        )
        OdinStatusReadout(
            primary = strings.getString(R.string.header_fan, telemetry.fan?.rpm?.toString() ?: "—"),
            supporting = "PWM ${telemetry.fan?.dutyPercent ?: "—"}%",
            modifier = Modifier.alignBy(FirstBaseline)
        )
        OdinStatusReadout(primary = clock, supporting = strings.getString(R.string.header_time),
            modifier = Modifier.alignBy(FirstBaseline))
    }
}
