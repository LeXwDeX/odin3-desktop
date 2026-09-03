package com.odin.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odin.desktop.data.model.InstalledApp
import com.odin.desktop.ui.navigation.FocusZone
import com.odin.desktop.ui.theme.CyanAccent
import com.odin.desktop.ui.theme.DarkSurface
import com.odin.desktop.ui.theme.TextDim

/**
 * 掌机控制台级大卡片滑带（严格屏幕级垂直绝对居中，Slot 固定 128dp，彻底杜绝相邻挤压与漂移）
 */
@Composable
fun AppHorizontalRow(
    apps: List<InstalledApp>,
    selectedAppIndex: Int,
    focusZone: FocusZone,
    isReordering: Boolean = false,
    pickedIndex: Int? = null,
    onAppClick: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        val density = LocalDensity.current
        // 计算居中偏移量：使当前高亮选中的图标严格平滑位于屏幕正中央
        val halfWidthPx = with(density) {
            val offset = (maxWidth - 128.dp) / 2
            offset.coerceAtLeast(0.dp).roundToPx()
        }

        // 焦点平滑居中滚动，保证光标位置在 view 中始终可见并居中
        LaunchedEffect(selectedAppIndex) {
            if (selectedAppIndex in apps.indices) {
                listState.animateScrollToItem(
                    index = selectedAppIndex,
                    scrollOffset = -halfWidthPx
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (apps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "暂无应用，按 X 键可管理或添加应用至此分类",
                        color = TextDim,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    itemsIndexed(apps) { index, app ->
                        val isFocused = index == selectedAppIndex && focusZone == FocusZone.APPS
                        val isPicked = isReordering && pickedIndex == index
                        AppCard(
                            app = app,
                            isFocused = isFocused,
                            isReordering = isReordering,
                            isPicked = isPicked,
                            cardIndex = index,
                            onClick = { onAppClick(app) }
                        )
                    }
                }
            }

            // 排序编辑模式提示条
            if (isReordering) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurface.copy(alpha = 0.92f))
                        .border(1.dp, CyanAccent.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (pickedIndex != null) "【已抓起】按方向键左右移动位置 • 按 A 键放下" else "【排序模式】按 A 键抓起图标 • 方向键左右选应用 • 按 B 键完成退出",
                        color = CyanAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
