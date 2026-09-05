package com.odin.desktop.ui.components

import com.odin.desktop.ui.theme.LocalOdinPalette
import androidx.compose.ui.platform.LocalContext
import com.odin.desktop.R
import com.odin.desktop.locale.AppLanguage
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/**
 * 掌机控制台级原生全屏设置浮层 (避免 Android Dialog Window 劫持手柄焦点)
 */
@Composable
fun ConfigDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    selectedSection: Int,
    inSubMenu: Boolean,
    subFocusIndex: Int,
    onSectionClick: (Int) -> Unit,
    currentJoystickColor: String,
    currentOrientation: Int,
    currentLanguage: AppLanguage,
    onLanguageSelect: (AppLanguage) -> Unit,
    isDefaultHome: Boolean = false,
    autoFanControlEnabled: Boolean,
    socTemp: Float,
    onColorSelect: (String) -> Unit,
    onOrientationSelect: (Int) -> Unit,
    onRequestDefaultHome: () -> Unit = {},
    onToggleAutoFan: () -> Unit,
    tabs: List<TabEntity>,
    tabActionFocusIndex: Int = 0,
    onAddTab: (String, Boolean) -> Unit,
    onRenameTab: (TabEntity, String) -> Unit,
    onDeleteTab: (TabEntity) -> Unit,
    onMoveTabUp: (TabEntity) -> Unit,
    onMoveTabDown: (TabEntity) -> Unit,
    onSetDefaultTab: (TabEntity) -> Unit
) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    if (!isOpen) return

    val sections = listOf(
        strings.getString(R.string.text_1_stick_light_color),
        strings.getString(R.string.text_2_screen_orientation),
        strings.getString(R.string.text_3_home_and_startup),
        strings.getString(R.string.text_4_automatic_fan),
        strings.getString(R.string.text_5_edit_tabs),
        strings.getString(R.string.language_section),
        strings.getString(R.string.text_7_about)
    )

    val menuScrollState = rememberLazyListState()
    LaunchedEffect(selectedSection) {
        menuScrollState.animateScrollToItem(selectedSection)
    }

    // 同一 Window 内的原生全屏遮罩，确保 D-Pad、A、B 键位事件完全直通
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background.copy(alpha = 0.96f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 32.dp)
        ) {
            // 顶栏标题与关闭指引
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.getString(R.string.text_system_settings),
                    color = palette.accent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = strings.getString(R.string.text_b_or_tap_the_background_to_return),
                    color = palette.textDim,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 左侧设置分类导航
                LazyColumn(
                    state = menuScrollState,
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surface)
                        .border(
                            width = if (!inSubMenu) 1.5.dp else 1.dp,
                            color = if (!inSubMenu) palette.accent.copy(alpha = 0.8f) else palette.border,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(sections) { index, title ->
                        val isSelected = selectedSection == index
                        val isMenuFocused = !inSubMenu && isSelected

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isMenuFocused) palette.accent.copy(alpha = 0.25f)
                                    else if (isSelected) palette.accent.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .border(
                                    width = if (isMenuFocused) 1.5.dp else 0.dp,
                                    color = if (isMenuFocused) palette.accent else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSectionClick(index) }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = title,
                                color = if (isMenuFocused || isSelected) palette.accent else palette.text,
                                fontSize = 15.sp,
                                fontWeight = if (isMenuFocused || isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // 右侧子内容配置区
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surface)
                        .border(
                            width = if (inSubMenu) 1.5.dp else 1.dp,
                            color = if (inSubMenu) palette.accent.copy(alpha = 0.5f) else palette.border,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(24.dp)
                ) {
                    when (selectedSection) {
                        0 -> ColorSection(currentJoystickColor, inSubMenu, subFocusIndex, onColorSelect)
                        1 -> OrientationSection(currentOrientation, inSubMenu, subFocusIndex, onOrientationSelect)
                        2 -> DefaultHomeAndBootSection(isDefaultHome, inSubMenu, onRequestDefaultHome)
                        3 -> AutoFanSection(autoFanControlEnabled, socTemp, inSubMenu, subFocusIndex, onToggleAutoFan)
                        4 -> TabEditSection(tabs, inSubMenu, subFocusIndex, tabActionFocusIndex, onAddTab, onRenameTab, onDeleteTab, onMoveTabUp, onMoveTabDown, onSetDefaultTab)
                        5 -> LanguageSection(currentLanguage, inSubMenu, subFocusIndex, onLanguageSelect)
                        6 -> AboutSection()
                    }
                }
            }
        }
    }
}
