package com.odin.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.odin.desktop.R
import com.odin.desktop.data.entity.TabAction
import com.odin.desktop.data.entity.TabEntity
import com.odin.desktop.data.entity.getAvailableTabActions
import com.odin.desktop.data.model.displayName
import com.odin.desktop.ui.components.base.*
import com.odin.desktop.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TabEditSection(
    tabs: List<TabEntity>, inSubMenu: Boolean, subFocusIndex: Int, tabActionFocusIndex: Int,
    onAddTab: (String, Boolean) -> Unit, onRenameTab: (TabEntity, String) -> Unit,
    onDeleteTab: (TabEntity) -> Unit, onMoveTabUp: (TabEntity) -> Unit,
    onMoveTabDown: (TabEntity) -> Unit, onSetDefaultTab: (TabEntity) -> Unit
) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    var newTabName by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(OdinSpacing.lg)) {
        SettingsSectionHeader(strings.getString(R.string.text_tab_groups_and_order_value_10, tabs.size),
            strings.getString(R.string.text_up_down_tab_left_right_action_a), descriptionColor = palette.textDim)
        OdinEqualHeightRow {
            OdinTextField(newTabName, { newTabName = it }, strings.getString(R.string.text_new_tab_name),
                modifier = Modifier.weight(1f).fillMaxHeight(), enabled = tabs.size < 10)
            OdinActionButton(strings.getString(R.string.text_add),
                enabled = tabs.size < 10 && newTabName.isNotBlank(),
                modifier = Modifier.width(OdinSizes.fieldActionWidth).fillMaxHeight(),
                onClick = { onAddTab(newTabName.trim(), false); newTabName = "" })
        }
        if (tabs.size >= 10) Text(strings.getString(R.string.text_you_can_create_up_to_10_tabs),
            style = OdinTypography.caption, color = palette.textDim)
        val listState = rememberLazyListState()
        LaunchedEffect(subFocusIndex, inSubMenu) {
            if (inSubMenu && subFocusIndex in tabs.indices) listState.animateScrollToItem(subFocusIndex)
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(OdinSpacing.sm)) {
            itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
                val available = getAvailableTabActions(tab, index, tabs.size)
                val focused = if (inSubMenu && subFocusIndex == index) available.getOrNull(tabActionFocusIndex) else null
                OdinSurface(Modifier.fillMaxWidth(), role = SurfaceRole.DENSE) {
                    FlowRow(modifier = Modifier.heightIn(min = OdinSizes.tagHeight()).wrapContentHeight(),
                        horizontalArrangement = Arrangement.spacedBy(OdinSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(OdinSpacing.xs)) {
                        Text("#${index + 1}", style = OdinTypography.caption, color = palette.textDim,
                            modifier = Modifier.align(Alignment.CenterVertically))
                        Text(tab.displayName(strings), style = OdinTypography.body, color = palette.text,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).align(Alignment.CenterVertically))
                        if (tab.isDefault) OdinBadge(strings.getString(R.string.text_home_tab), BadgeRole.ACTIVE)
                        if (tab.isGameTab) OdinBadge(strings.getString(R.string.text_game_category), BadgeRole.INFO)
                    }
                    Spacer(Modifier.height(OdinSpacing.sm))
                    OdinEqualHeightRow {
                        TabAction.entries.forEach { action ->
                            val symbol = action == TabAction.MOVE_UP || action == TabAction.MOVE_DOWN
                            val label = when (action) {
                                TabAction.MOVE_UP -> "▲"
                                TabAction.MOVE_DOWN -> "▼"
                                TabAction.SET_DEFAULT -> strings.getString(R.string.text_set_as_home)
                                TabAction.DELETE -> strings.getString(R.string.text_delete)
                            }
                            OdinActionButton(label, enabled = action in available, focused = focused == action,
                                dangerous = action == TabAction.DELETE, iconOnly = symbol,
                                accessibilityLabel = when (action) {
                                    TabAction.MOVE_UP -> strings.getString(R.string.text_move_up)
                                    TabAction.MOVE_DOWN -> strings.getString(R.string.text_move_down)
                                    else -> null
                                },
                                modifier = (if (symbol) Modifier.width(OdinSizes.scaledControlHeight()) else Modifier.weight(1f)).fillMaxHeight(),
                                onClick = {
                                    when (action) {
                                        TabAction.MOVE_UP -> onMoveTabUp(tab)
                                        TabAction.MOVE_DOWN -> onMoveTabDown(tab)
                                        TabAction.SET_DEFAULT -> onSetDefaultTab(tab)
                                        TabAction.DELETE -> onDeleteTab(tab)
                                    }
                                })
                        }
                    }
                }
            }
        }
    }
}
