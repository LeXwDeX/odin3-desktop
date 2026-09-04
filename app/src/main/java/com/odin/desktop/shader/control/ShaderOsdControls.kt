package com.odin.desktop.shader.control

import com.odin.desktop.ui.theme.LocalOdinPalette
import androidx.compose.ui.platform.LocalContext
import com.odin.desktop.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odin.desktop.ui.theme.CyanAccent
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 掌机 TVGAME 级电视画质校准与滤镜调整控制台 (TV Display Calibration OSD)。
 * 提供 100% 实体手柄盲操体验，全屏内置广播级 SMPTE 测试图、灰度标定阶梯与特丽珑 OSD 调屏菜单。
 */
@Composable
internal fun OsdRow(
    title: String,
    valueText: String,
    isSelected: Boolean,
    valueColor: Color = CyanAccent,
    onClick: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit
) {
    val palette = LocalOdinPalette.current
    val bg = if (isSelected) Color(0x3300E5FF) else Color(0xFF0F1522)
    val border = if (isSelected) palette.accent else Color(0xFF1B2434)
    val cleanValueText = valueText.removePrefix("◄ ").removeSuffix(" ►")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(if (isSelected) 1.5.dp else 1.dp, border, RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isSelected) {
                Text(text = "►", color = palette.accent, fontSize = 11.sp)
            }
            Text(
                text = title,
                color = if (isSelected) palette.text else Color(0xFFB0C0D4),
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onLeft() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "◄", color = if (isSelected) palette.accent else Color(0xFF5A708C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = cleanValueText,
                color = valueColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onRight() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "►", color = if (isSelected) palette.accent else Color(0xFF5A708C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun OsdSliderRow(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    unit: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit
) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    val bg = if (isSelected) Color(0x3300E5FF) else Color(0xFF0F1522)
    val border = if (isSelected) palette.accent else Color(0xFF1B2434)
    val isClosed = (value == 0 && unit == strings.getString(R.string.text_off))
    val sign = if (value > 0 && unit == "%") "+" else ""
    val ratio = if (isClosed) 0f else ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f)
    val barCount = 10
    val filled = (ratio * barCount).roundToInt()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(if (isSelected) 1.5.dp else 1.dp, border, RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isSelected) {
                Text(text = "►", color = palette.accent, fontSize = 11.sp)
            }
            Text(
                text = title,
                color = if (isSelected) palette.text else Color(0xFFB0C0D4),
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // ◄ 独立触控减小按钮
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onLeft() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "◄",
                    color = if (isSelected) palette.accent else Color(0xFF5A708C),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 点阵/段落刻度条
            val barStr = "▮".repeat(filled) + "▯".repeat(barCount - filled)
            Text(
                text = barStr,
                color = if (isClosed) Color(0xFF3A4B60) else if (isSelected) palette.accent else Color(0xFF5A708C),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            // ► 独立触控增加按钮
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onRight() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "►",
                    color = if (isSelected) palette.accent else Color(0xFF5A708C),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = if (isClosed) strings.getString(R.string.text_off) else "$sign$value$unit",
                color = if (isClosed) palette.textDim else if (value != 0) palette.accent else palette.textDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
internal fun OsdSliderRowFloat(
    title: String,
    value: Float,
    min: Float,
    max: Float,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit
) {
    val palette = LocalOdinPalette.current
    val bg = if (isSelected) Color(0x3300E5FF) else Color(0xFF0F1522)
    val border = if (isSelected) palette.accent else Color(0xFF1B2434)
    val ratio = ((value - min) / (max - min)).coerceIn(0f, 1f)
    val barCount = 10
    val filled = (ratio * barCount).roundToInt()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(if (isSelected) 1.5.dp else 1.dp, border, RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isSelected) {
                Text(text = "►", color = palette.accent, fontSize = 11.sp)
            }
            Text(
                text = title,
                color = if (isSelected) palette.text else Color(0xFFB0C0D4),
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // ◄ 独立触控减小按钮
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onLeft() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "◄",
                    color = if (isSelected) palette.accent else Color(0xFF5A708C),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            val barStr = "▮".repeat(filled) + "▯".repeat(barCount - filled)
            Text(
                text = barStr,
                color = if (isSelected) palette.accent else Color(0xFF5A708C),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            // ► 独立触控增加按钮
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onRight() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "►",
                    color = if (isSelected) palette.accent else Color(0xFF5A708C),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = String.format(Locale.US, "%.2f", value),
                color = palette.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
internal fun OsdToggleRow(
    title: String,
    enabled: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    val bg = if (isSelected) Color(0x3300E5FF) else Color(0xFF0F1522)
    val border = if (isSelected) palette.accent else Color(0xFF1B2434)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(if (isSelected) 1.5.dp else 1.dp, border, RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isSelected) {
                Text(text = "►", color = palette.accent, fontSize = 11.sp)
            }
            Text(
                text = title,
                color = if (isSelected) palette.text else Color(0xFFB0C0D4),
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
        Text(
            text = if (enabled) strings.getString(R.string.text_on) else strings.getString(R.string.text_off_2),
            color = if (enabled) palette.active else palette.textDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
internal fun LegendChip(button: String, label: String, highlight: Boolean = false) {
    val palette = LocalOdinPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (highlight) palette.warning else Color(0xFF243248))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = button,
                color = if (highlight) palette.background else Color(0xFFD4E2F5),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = label,
            color = Color(0xFFA6B7CE),
            fontSize = 11.sp
        )
    }
}
