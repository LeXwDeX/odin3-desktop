package com.odin.desktop.ui.components

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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.sp
import com.odin.desktop.data.entity.TabEntity
import com.odin.desktop.ui.navigation.FocusZone

@Composable
fun TopTabBar(
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
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ShoulderButtonBadge(label = "L1")
        val listState = rememberLazyListState()
        val activeIndex = if (isDashboardSelected) 0 else selectedTabIndex + 1
        LaunchedEffect(activeIndex) { listState.animateScrollToItem(activeIndex) }
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "dashboard") {
                HomeTab(strings.getString(com.odin.desktop.R.string.page_dashboard), isDashboardSelected && !isConfigFocused, focusZone, onDashboardSelected)
            }
            itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
                HomeTab(tab.displayName(strings).uppercase(), !isDashboardSelected && selectedTabIndex == index && !isConfigFocused,
                    focusZone) { onTabSelected(index) }
            }
        }
        ShoulderButtonBadge(label = "R1")
        Spacer(Modifier.padding(horizontal = 6.dp))

        // 右侧固定 [CONFIG] 设置按钮
        val isConfigSelected = isConfigFocused && focusZone == FocusZone.TABS
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isConfigSelected) palette.accent.copy(alpha = 0.2f) else palette.surface)
                .border(
                    width = 1.dp,
                    color = if (isConfigSelected) palette.accent else Color.Transparent,
                    shape = RoundedCornerShape(6.dp)
                )
                .clickable { onConfigClick() }
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = strings.getString(com.odin.desktop.R.string.page_config),
                color = if (isConfigSelected) palette.accent else palette.text,
                fontSize = 14.sp,
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
        Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (focused) palette.accent.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (selected) palette.accent else palette.textDim, fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
fun ShoulderButtonBadge(label: String) {
    val palette = LocalOdinPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(palette.surface)
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = palette.textDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
