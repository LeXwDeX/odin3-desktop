package com.odin.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.odin.desktop.data.entity.TabEntity
import com.odin.desktop.ui.navigation.FocusZone
import com.odin.desktop.ui.theme.CyanAccent
import com.odin.desktop.ui.theme.DarkSurface
import com.odin.desktop.ui.theme.PureBlack
import com.odin.desktop.ui.theme.TextDim
import com.odin.desktop.ui.theme.TextWhite

@Composable
fun TopTabBar(
    tabs: List<TabEntity>,
    selectedTabIndex: Int,
    isConfigFocused: Boolean,
    focusZone: FocusZone,
    onTabSelected: (Int) -> Unit,
    onConfigClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // L1 肩键提示
            ShoulderButtonBadge(label = "L1")

            tabs.forEachIndexed { index, tab ->
                val isSelected = selectedTabIndex == index && !isConfigFocused
                val isTabFocused = isSelected && focusZone == FocusZone.TABS

                val bgColor by animateColorAsState(
                    targetValue = if (isTabFocused) CyanAccent.copy(alpha = 0.2f) else Color.Transparent,
                    label = "tab_bg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) CyanAccent else TextDim,
                    label = "tab_text"
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(bgColor)
                        .clickable { onTabSelected(index) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = tab.name.uppercase(),
                        color = textColor,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            // R1 肩键提示
            ShoulderButtonBadge(label = "R1")
        }

        // 右侧固定 [CONFIG] 设置按钮
        val isConfigSelected = isConfigFocused && focusZone == FocusZone.TABS
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isConfigSelected) CyanAccent.copy(alpha = 0.2f) else DarkSurface)
                .border(
                    width = 1.dp,
                    color = if (isConfigSelected) CyanAccent else Color.Transparent,
                    shape = RoundedCornerShape(6.dp)
                )
                .clickable { onConfigClick() }
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "⚙️ CONFIG",
                color = if (isConfigSelected) CyanAccent else TextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ShoulderButtonBadge(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(DarkSurface)
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = TextDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
