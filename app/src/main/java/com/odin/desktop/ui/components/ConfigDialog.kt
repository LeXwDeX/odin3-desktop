package com.odin.desktop.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.odin.desktop.R
import com.odin.desktop.data.entity.TabEntity
import com.odin.desktop.locale.AppLanguage
import com.odin.desktop.ui.components.base.OdinControl
import com.odin.desktop.ui.components.base.OdinSurface
import com.odin.desktop.ui.components.base.SurfaceRole
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.theme.OdinSpacing
import com.odin.desktop.ui.theme.OdinTypography

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
    onColorSelect: (String) -> Unit,
    onOrientationSelect: (Int) -> Unit,
    onRequestDefaultHome: () -> Unit = {},
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
        strings.getString(R.string.text_4_edit_tabs),
        strings.getString(R.string.language_section),
        strings.getString(R.string.text_6_about)
    )

    val menuScrollState = rememberLazyListState()
    LaunchedEffect(selectedSection) {
        menuScrollState.animateScrollToItem(selectedSection)
    }

    // 同一 Window 内的原生全屏遮罩，确保 D-Pad、A、B 键位事件完全直通
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(OdinSpacing.page)
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
                    style = OdinTypography.h1,
                    modifier = Modifier.weight(1f).padding(end = OdinSpacing.lg)
                )
                Text(
                    text = strings.getString(R.string.text_b_or_tap_the_background_to_return),
                    color = palette.textDim,
                    style = OdinTypography.caption,
                    modifier = Modifier.widthIn(max = 280.dp)
                )
            }

            Spacer(modifier = Modifier.height(OdinSpacing.xl))

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(OdinSpacing.xl)
            ) {
                // 左侧设置分类导航
                OdinSurface(Modifier.width(220.dp).fillMaxHeight(), SurfaceRole.NAVIGATION) {
                    LazyColumn(
                        state = menuScrollState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(OdinSpacing.sm)
                    ) {
                        itemsIndexed(sections) { index, title ->
                            val isSelected = selectedSection == index
                            val isMenuFocused = !inSubMenu && isSelected

                            OdinControl(
                                text = title,
                                onClick = { onSectionClick(index) },
                                selected = isSelected,
                                focused = isMenuFocused,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // 右侧子内容配置区
                OdinSurface(Modifier.weight(1f).fillMaxHeight(), SurfaceRole.PANEL) {
                    when (selectedSection) {
                        0 -> ColorSection(currentJoystickColor, inSubMenu, subFocusIndex, onColorSelect)
                        1 -> OrientationSection(currentOrientation, inSubMenu, subFocusIndex, onOrientationSelect)
                        2 -> DefaultHomeAndBootSection(isDefaultHome, inSubMenu, onRequestDefaultHome)
                        3 -> TabEditSection(tabs, inSubMenu, subFocusIndex, tabActionFocusIndex, onAddTab, onRenameTab, onDeleteTab, onMoveTabUp, onMoveTabDown, onSetDefaultTab)
                        4 -> LanguageSection(currentLanguage, inSubMenu, subFocusIndex, onLanguageSelect)
                        5 -> AboutSection()
                    }
                }
            }
        }
    }
}
