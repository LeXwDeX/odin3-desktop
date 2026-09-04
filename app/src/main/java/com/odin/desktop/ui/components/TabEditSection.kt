package com.odin.desktop.ui.components

import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.data.model.displayName
import androidx.compose.ui.platform.LocalContext
import com.odin.desktop.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odin.desktop.data.entity.TabEntity

@Composable
internal fun TabEditSection(
    tabs: List<TabEntity>,
    inSubMenu: Boolean,
    subFocusIndex: Int,
    tabActionFocusIndex: Int,
    onAddTab: (String, Boolean) -> Unit,
    onRenameTab: (TabEntity, String) -> Unit,
    onDeleteTab: (TabEntity) -> Unit,
    onMoveTabUp: (TabEntity) -> Unit,
    onMoveTabDown: (TabEntity) -> Unit,
    onSetDefaultTab: (TabEntity) -> Unit
) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    var newTabName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.getString(R.string.text_tab_groups_and_order_value_10, tabs.size), color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(strings.getString(R.string.text_up_down_tab_left_right_action_a), color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (tabs.size < 10) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newTabName,
                    onValueChange = { newTabName = it },
                    placeholder = { Text(strings.getString(R.string.text_new_tab_name), color = palette.textDim) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.text,
                        unfocusedTextColor = palette.text,
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.border
                    ),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (newTabName.isNotBlank()) {
                            onAddTab(newTabName.trim(), false)
                            newTabName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) {
                    Text(strings.getString(R.string.text_add), color = palette.background, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val listState = rememberLazyListState()
        LaunchedEffect(subFocusIndex, inSubMenu) {
            if (inSubMenu && subFocusIndex in tabs.indices) {
                listState.animateScrollToItem(subFocusIndex)
            }
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(tabs) { index, tab ->
                val isRowFocused = inSubMenu && subFocusIndex == index
                val availableActions = remember(tab, index, tabs.size) {
                    com.odin.desktop.data.entity.getAvailableTabActions(tab, index, tabs.size)
                }
                val focusedAction = if (isRowFocused) availableActions.getOrNull(tabActionFocusIndex) else null

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isRowFocused) palette.accent.copy(alpha = 0.12f) else palette.card)
                        .border(
                            width = if (isRowFocused) 2.dp else 1.dp,
                            color = if (isRowFocused) palette.accent.copy(alpha = 0.7f) else palette.border,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "#${index + 1}",
                            color = if (isRowFocused) palette.accent else palette.textDim,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tab.displayName(strings),
                            color = if (isRowFocused) palette.accent else palette.text,
                            fontSize = 15.sp,
                            fontWeight = if (isRowFocused || tab.isDefault) FontWeight.Bold else FontWeight.Normal
                        )
                        if (tab.isDefault) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFFFB300))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = strings.getString(R.string.text_home_tab),
                                    color = palette.background,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (tab.isGameTab) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(palette.accent.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = strings.getString(R.string.text_game_category),
                                    color = palette.accent,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // 排序与操作按钮区 (支持左右光标高亮聚焦或手柄键位直达)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 上移按钮
                        if (index > 0) {
                            val isBtnFocused = focusedAction == com.odin.desktop.data.entity.TabAction.MOVE_UP
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isBtnFocused) palette.accent else palette.surface)
                                    .border(
                                        width = if (isBtnFocused) 2.dp else 1.dp,
                                        color = if (isBtnFocused) palette.accent else palette.border,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onMoveTabUp(tab) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = strings.getString(R.string.text_move_up),
                                    color = if (isBtnFocused) palette.background else palette.text,
                                    fontSize = 11.sp,
                                    fontWeight = if (isBtnFocused) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        // 下移按钮
                        if (index < tabs.size - 1) {
                            val isBtnFocused = focusedAction == com.odin.desktop.data.entity.TabAction.MOVE_DOWN
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isBtnFocused) palette.accent else palette.surface)
                                    .border(
                                        width = if (isBtnFocused) 2.dp else 1.dp,
                                        color = if (isBtnFocused) palette.accent else palette.border,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onMoveTabDown(tab) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = strings.getString(R.string.text_move_down),
                                    color = if (isBtnFocused) palette.background else palette.text,
                                    fontSize = 11.sp,
                                    fontWeight = if (isBtnFocused) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        // 设为默认首页按钮
                        if (!tab.isDefault) {
                            val isBtnFocused = focusedAction == com.odin.desktop.data.entity.TabAction.SET_DEFAULT
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isBtnFocused) palette.accent else palette.accent.copy(alpha = 0.2f))
                                    .border(
                                        width = if (isBtnFocused) 2.dp else 1.dp,
                                        color = palette.accent,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onSetDefaultTab(tab) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = strings.getString(R.string.text_set_as_home),
                                    color = if (isBtnFocused) palette.background else palette.accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 删除按钮
                        if (!tab.isDefault && tab.kind != com.odin.desktop.data.entity.TabKind.ALL_APPS) {
                            val isBtnFocused = focusedAction == com.odin.desktop.data.entity.TabAction.DELETE
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isBtnFocused) palette.danger else palette.danger.copy(alpha = 0.15f))
                                    .border(
                                        width = if (isBtnFocused) 2.dp else 1.dp,
                                        color = if (isBtnFocused) palette.danger else palette.danger.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onDeleteTab(tab) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = strings.getString(R.string.text_delete),
                                    color = if (isBtnFocused) palette.background else palette.danger,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
