package com.odin.desktop.ui.components.base

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
import com.odin.desktop.ui.theme.CardBorder
import com.odin.desktop.ui.theme.CyanAccent
import com.odin.desktop.ui.theme.DarkSurface
import com.odin.desktop.ui.theme.PureBlack
import com.odin.desktop.ui.theme.RedDanger
import com.odin.desktop.ui.theme.TextDim
import com.odin.desktop.ui.theme.TextWhite

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
    footerHint: String = "【上下键选择 • A 键确认 • B 键返回】",
    maxWidth: Dp = 680.dp,
    maxHeight: Dp = 420.dp,
    content: @Composable BoxScope.() -> Unit
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        // 全屏半透深黑遮罩 (同一 Window，零焦点劫持)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PureBlack.copy(alpha = 0.88f))
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
                    .background(DarkSurface)
                    .border(
                        width = 1.5.dp,
                        color = CyanAccent.copy(alpha = 0.85f),
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
                                color = TextWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (badgeText != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(CyanAccent.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        color = CyanAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 头部快捷 B 键返回提示
                        Text(
                            text = "【B 键 / 触碰空白返回】",
                            color = TextDim,
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
                            text = footerHint,
                            color = CyanAccent,
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
    val bgColor = when {
        isFocused && isDanger -> RedDanger
        isFocused -> CyanAccent
        isSelected -> CyanAccent.copy(alpha = 0.12f)
        else -> PureBlack
    }

    val contentColor = when {
        isFocused -> PureBlack
        isDanger -> RedDanger
        isSelected -> CyanAccent
        else -> TextWhite
    }

    val borderColor = when {
        isFocused && isDanger -> RedDanger
        isFocused -> CyanAccent
        isSelected -> CyanAccent.copy(alpha = 0.5f)
        else -> CardBorder
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
                        color = if (isFocused) PureBlack.copy(alpha = 0.7f) else TextDim,
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
                color = if (isFocused) PureBlack else CyanAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
