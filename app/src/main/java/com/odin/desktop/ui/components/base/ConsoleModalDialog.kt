package com.odin.desktop.ui.components.base

import com.odin.desktop.ui.theme.LocalOdinPalette
import androidx.compose.ui.platform.LocalContext
import com.odin.desktop.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Odin 3 掌机级原生模态框规范架构基类 (同一 Window 内原生全屏遮罩，避免任何 Window 劫持按键焦点)。
 * 具备沉浸式纯黑半透遮罩、OLED 深色面板、青色外发光边框、标准头部（标题/分类徽章/B键提示）与底部手柄引导条。
 */
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
                ),
            contentAlignment = Alignment.Center
        ) {
            // 居中模态框主体卡片
            Box(
                modifier = Modifier
                    .width(maxWidth)
                    .height(maxHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surface)
                    .border(
                        width = 1.5.dp,
                        color = palette.accent.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* 消费点击防穿透 */ }
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. 标准化头部区域
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            titleIcon?.invoke()
                            Text(
                                text = title,
                                color = palette.text,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (badgeText != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(palette.accent.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        color = palette.accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 头部快捷 B 键返回提示
                        Text(
                            text = strings.getString(R.string.text_b_or_tap_the_background_to_return_2),
                            color = palette.textDim,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. 模态框插槽内容区域
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        content()
                    }

                    // 3. 标准化底部手柄快捷导航引导条
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = footerHint ?: strings.getString(R.string.text_up_down_select_a_confirm_b_back),
                            color = palette.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalOdinPalette.current
    val bgColor = when {
        isFocused && isDanger -> palette.danger
        isFocused -> palette.accent
        isSelected -> palette.accent.copy(alpha = 0.12f)
        else -> palette.background
    }

    val contentColor = when {
        isFocused -> palette.background
        isDanger -> palette.danger
        isSelected -> palette.accent
        else -> palette.text
    }

    val borderColor = when {
        isFocused && isDanger -> palette.danger
        isFocused -> palette.accent
        isSelected -> palette.accent.copy(alpha = 0.5f)
        else -> palette.border
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            icon?.invoke()
            Column {
                Text(
                    text = title,
                    color = contentColor,
                    fontSize = 14.sp,
                    fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = if (isFocused) palette.background.copy(alpha = 0.7f) else palette.textDim,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (trailingText != null) {
            Text(
                text = trailingText,
                color = if (isFocused) palette.background else palette.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
