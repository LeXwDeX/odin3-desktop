package com.odin.desktop.ui.components.base

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.odin.desktop.R
import com.odin.desktop.ui.components.base.BadgeRole
import com.odin.desktop.ui.components.base.OdinBadge
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.theme.OdinSpacing
import com.odin.desktop.ui.theme.OdinTypography

/**
 * Odin 3 掌机级原生模态框规范架构基类 (同一 Window 内原生全屏遮罩，避免任何 Window 劫持按键焦点)。
 * Shared surface, heading, content and controller hint in the launcher window.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConsoleModalDialog(
    isOpen: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    titleIcon: @Composable (() -> Unit)? = null,
    badgeText: String? = null,
    footerHint: String? = null,
    maxWidth: Dp = 680.dp,
    maxHeight: Dp = 420.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        // 全屏半透深黑遮罩 (同一 Window，零焦点劫持)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background.copy(alpha = 0.88f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ).padding(OdinSpacing.page),
            contentAlignment = Alignment.Center
        ) {
            // 居中模态框主体卡片
            OdinSurface(
                modifier = Modifier.width(maxWidth).height(maxHeight).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { /* Consume clicks inside the modal. */ },
                role = SurfaceRole.MODAL
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. 标准化头部区域
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FlowRow(
                            modifier = Modifier.weight(1f).padding(end = OdinSpacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(OdinSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(OdinSpacing.sm)
                        ) {
                            Row(Modifier.weight(1f).align(Alignment.CenterVertically),
                                horizontalArrangement = Arrangement.spacedBy(OdinSpacing.md),
                                verticalAlignment = Alignment.CenterVertically) {
                                titleIcon?.invoke()
                                Text(title, modifier = Modifier.weight(1f), maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, color = palette.text, style = OdinTypography.h2)
                            }
                            if (badgeText != null) OdinBadge(badgeText, BadgeRole.INFO,
                                Modifier.align(Alignment.CenterVertically))
                        }

                        // 头部快捷 B 键返回提示
                        Text(
                            text = strings.getString(R.string.text_b_or_tap_the_background_to_return_2),
                            modifier = Modifier.widthIn(max = 180.dp),
                            color = palette.textDim,
                            style = OdinTypography.caption
                        )
                    }

                    Spacer(modifier = Modifier.height(OdinSpacing.lg))

                    // 2. 模态框插槽内容区域
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        content()
                    }

                    // 3. 标准化底部手柄快捷导航引导条
                    Spacer(modifier = Modifier.height(OdinSpacing.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = footerHint ?: strings.getString(R.string.text_up_down_select_a_confirm_b_back),
                            color = palette.accent,
                            style = OdinTypography.caption
                        )
                    }
                }
            }
        }
    }
}

/**
 * 标准模态框列表项组件。
 * 具备手柄高亮聚焦状态（青色高亮背景）、主副文本、状态徽章与危险项渲染。
 */
@Composable
fun ConsoleDialogItem(
    title: String,
    subtitle: String? = null,
    icon: @Composable (() -> Unit)? = null,
    isFocused: Boolean,
    isSelected: Boolean = false,
    trailingText: String? = null,
    isDanger: Boolean = false,
    enabled: Boolean = true,
    radio: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OdinControl(
        text = title, subtitle = subtitle, icon = icon,
        focused = isFocused, selected = isSelected, badge = trailingText, enabled = enabled, radio = radio,
        dangerous = isDanger, onClick = onClick, modifier = modifier.fillMaxWidth()
    )
}
