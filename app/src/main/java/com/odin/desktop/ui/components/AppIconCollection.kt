package com.odin.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odin.desktop.R
import com.odin.desktop.data.model.HOME_APP_LIMIT
import com.odin.desktop.data.model.InstalledApp
import com.odin.desktop.ui.theme.LocalOdinPalette
import kotlin.math.roundToInt

private class IconDragState {
    var app by mutableStateOf<InstalledApp?>(null)
    var position by mutableStateOf(Offset.Zero)
    var moved by mutableStateOf(false)
}

/** One gesture owner survives keyed item moves and scrolling in either layout. */
@Composable
fun AppIconCollection(
    apps: List<InstalledApp>, selectedIndex: Int, hasFocus: Boolean,
    isGrid: Boolean, isReordering: Boolean, pickedIndex: Int?,
    collectionKey: Any, sortKey: Any,
    onClick: (InstalledApp, Int) -> Unit,
    onPick: (String) -> Unit, onMove: (String, String) -> Unit, onDrop: () -> Unit,
    onAllApps: () -> Unit, onColumns: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalOdinPalette.current
    val visibleApps = if (isGrid || isReordering) apps else apps.take(HOME_APP_LIMIT)
    val hasMore = !isGrid && !isReordering && apps.size > HOME_APP_LIMIT
    val row = key(collectionKey) { rememberLazyListState() }
    val grid = rememberLazyGridState()
    val drag = remember { IconDragState() }
    val currentApps by rememberUpdatedState(visibleApps)
    val pick by rememberUpdatedState(onPick)
    val move by rememberUpdatedState(onMove)
    val drop by rememberUpdatedState(onDrop)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val iconSize = with(density) { (if (isGrid) 88.dp else 128.dp).toPx() }
    val edge = with(density) { 48.dp.toPx() }
    val speed = with(density) { 560.dp.toPx() }

    BoxWithConstraints(modifier) {
        val columns = (maxWidth / 128.dp).toInt().coerceAtLeast(1)
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        SideEffect { if (isGrid) onColumns(columns) }

        fun targetAt(position: Offset): String? = if (isGrid) {
            grid.layoutInfo.visibleItemsInfo.firstOrNull {
                Rect(it.offset.x.toFloat(), it.offset.y.toFloat(),
                    (it.offset.x + it.size.width).toFloat(), (it.offset.y + it.size.height).toFloat()).contains(position)
            }?.key as? String
        } else {
            row.layoutInfo.visibleItemsInfo.firstOrNull {
                position.x >= it.offset && position.x < it.offset + it.size && position.y in 0f..height
            }?.key as? String
        }
        fun moveToPointer() {
            val source = drag.app?.packageName ?: return
            val target = targetAt(drag.position) ?: return
            if (source != target && currentApps.any { it.packageName == target }) move(source, target)
        }
        val moveAtPointer by rememberUpdatedState({ moveToPointer() })
        LaunchedEffect(drag.app?.packageName, drag.moved) {
            if (drag.app == null || !drag.moved) return@LaunchedEffect
            var previous = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(0.04f)
                previous = now
                val position = if (isGrid) drag.position.y else drag.position.x
                val end = if (isGrid) height else width
                val fraction = when {
                    position < edge -> -((edge - position) / edge).coerceIn(0f, 1f)
                    position > end - edge -> ((position - end + edge) / edge).coerceIn(0f, 1f)
                    else -> 0f
                }
                if (fraction != 0f) {
                    if (isGrid) grid.scrollBy(fraction * speed * seconds) else row.scrollBy(fraction * speed * seconds)
                    moveAtPointer()
                }
            }
        }
        LaunchedEffect(selectedIndex, isGrid, drag.app == null, hasMore, collectionKey, sortKey) {
            if (drag.app != null) return@LaunchedEffect
            if (isGrid && selectedIndex in visibleApps.indices) {
                val item = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
                if (item == null || item.offset.y < 0 || item.offset.y + item.size.height > height)
                    grid.animateScrollToItem(selectedIndex)
            } else if (!isGrid && selectedIndex in 0 until visibleApps.size + if (hasMore) 1 else 0) {
                row.animateScrollToItem(selectedIndex, -((width - iconSize) / 2).roundToInt())
            }
        }
        Box(Modifier.fillMaxSize().pointerInput(isGrid, collectionKey) {
            detectDragGesturesAfterLongPress(
                onDragStart = { position ->
                    val key = targetAt(position)
                    val app = currentApps.firstOrNull { it.packageName == key }
                    if (app != null) {
                        drag.position = position
                        drag.moved = false
                        drag.app = app
                        pick(app.packageName)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
                onDrag = { change, amount ->
                    if (drag.app != null) {
                        change.consume()
                        drag.position += amount
                        if (amount.getDistance() > 0f) drag.moved = true
                        moveAtPointer()
                    }
                },
                onDragEnd = {
                    if (drag.app != null && drag.moved) drop()
                    drag.app = null
                },
                onDragCancel = {
                    if (drag.app != null) drop()
                    drag.app = null
                }
            )
        }) {
            if (visibleApps.isEmpty()) {
                Text(stringResource(R.string.text_no_apps_in_this_category), color = palette.textDim,
                    modifier = Modifier.align(Alignment.Center))
            } else if (isGrid) {
                LazyVerticalGrid(columns = GridCells.Fixed(columns), state = grid,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = drag.app == null, modifier = Modifier.fillMaxSize()) {
                    gridItemsIndexed(visibleApps, key = { _, app -> app.packageName }) { index, app ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppCard(app, hasFocus && selectedIndex == index, isReordering, pickedIndex == index,
                                index, { onClick(app, index) },
                                modifier = Modifier, compact = true, hidden = drag.app?.packageName == app.packageName,
                                onLongClick = { onPick(app.packageName) })
                            Text(app.label, color = palette.text, fontSize = 12.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
                        }
                    }
                }
            } else {
                LazyRow(state = row, contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
                    userScrollEnabled = drag.app == null, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(visibleApps, key = { _, app -> app.packageName }) { index, app ->
                        AppCard(app, hasFocus && selectedIndex == index, isReordering, pickedIndex == index,
                            index, { onClick(app, index) }, hidden = drag.app?.packageName == app.packageName,
                            onLongClick = { onPick(app.packageName) })
                    }
                    if (hasMore) item(key = "all-apps-entry") {
                        val label = stringResource(R.string.app_library)
                        Column(Modifier.size(128.dp).border(2.dp,
                            if (hasFocus && selectedIndex == HOME_APP_LIMIT) palette.accent else palette.border,
                            RoundedCornerShape(16.dp)).background(palette.card, RoundedCornerShape(16.dp))
                            .clickable(onClick = onAllApps).semantics { contentDescription = label },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("+", fontSize = 48.sp, color = palette.accent)
                            Text(label, fontSize = 13.sp, color = palette.text, maxLines = 2,
                                overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }
                }
            }
            drag.app?.let { app ->
                AppCard(app, true, isPicked = true, onClick = {}, interactive = false, compact = isGrid,
                    modifier = Modifier.offset { IntOffset((drag.position.x - iconSize / 2).roundToInt(),
                        (drag.position.y - iconSize / 2).roundToInt()) })
            }
        }
    }
}
