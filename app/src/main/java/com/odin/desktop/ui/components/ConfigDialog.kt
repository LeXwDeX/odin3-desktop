package com.odin.desktop.ui.components

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.odin.desktop.service.fan.HardwareController
import com.odin.desktop.ui.theme.CardBackground
import com.odin.desktop.ui.theme.CardBorder
import com.odin.desktop.ui.theme.CyanAccent
import com.odin.desktop.ui.theme.DarkSurface
import com.odin.desktop.ui.theme.GreenActive
import com.odin.desktop.ui.theme.OrangeWarning
import com.odin.desktop.ui.theme.PureBlack
import com.odin.desktop.ui.theme.RedDanger
import com.odin.desktop.ui.theme.TextDim
import com.odin.desktop.ui.theme.TextWhite

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
    autoFanControlEnabled: Boolean,
    socTemp: Float,
    onColorSelect: (String) -> Unit,
    onOrientationSelect: (Int) -> Unit,
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
    if (!isOpen) return

    val sections = listOf("1. 摇杆灯颜色", "2. 屏幕方向规则", "3. 自动风扇控制", "4. Tab 页编辑", "5. 关于")

    // 同一 Window 内的原生全屏遮罩，确保 D-Pad、A、B 键位事件完全直通
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack.copy(alpha = 0.96f))
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
                    text = "系统设置",
                    color = CyanAccent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "【B 键 / 点击空白返回桌面】",
                    color = TextDim,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 左侧设置分类导航
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .border(
                            width = if (!inSubMenu) 1.5.dp else 1.dp,
                            color = if (!inSubMenu) CyanAccent.copy(alpha = 0.8f) else CardBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sections.forEachIndexed { index, title ->
                        val isSelected = selectedSection == index
                        val isMenuFocused = !inSubMenu && isSelected

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isMenuFocused) CyanAccent.copy(alpha = 0.25f)
                                    else if (isSelected) CyanAccent.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .border(
                                    width = if (isMenuFocused) 1.5.dp else 0.dp,
                                    color = if (isMenuFocused) CyanAccent else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSectionClick(index) }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = title,
                                color = if (isMenuFocused || isSelected) CyanAccent else TextWhite,
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
                        .background(DarkSurface)
                        .border(
                            width = if (inSubMenu) 1.5.dp else 1.dp,
                            color = if (inSubMenu) CyanAccent.copy(alpha = 0.5f) else CardBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(24.dp)
                ) {
                    when (selectedSection) {
                        0 -> ColorSection(currentJoystickColor, inSubMenu, subFocusIndex, onColorSelect)
                        1 -> OrientationSection(currentOrientation, inSubMenu, subFocusIndex, onOrientationSelect)
                        2 -> AutoFanSection(autoFanControlEnabled, socTemp, inSubMenu, subFocusIndex, onToggleAutoFan)
                        3 -> TabEditSection(tabs, inSubMenu, subFocusIndex, tabActionFocusIndex, onAddTab, onRenameTab, onDeleteTab, onMoveTabUp, onMoveTabDown, onSetDefaultTab)
                        4 -> AboutSection()
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSection(
    currentColor: String,
    inSubMenu: Boolean,
    subFocusIndex: Int,
    onColorSelect: (String) -> Unit
) {
    val presets = listOf(
        Pair("青蓝(默认)", "#ff00e5ff"),
        Pair("极客紫", "#ff7c4dff"),
        Pair("战斗红", "#ffff5252"),
        Pair("荧光绿", "#ff00e676"),
        Pair("冰川白", "#ffffffff"),
        Pair("暗夜灰", "#ff2e2e2e")
    )

    Column {
        Text("选择摇杆 LED 灯光色彩预设（A键即时确认）：", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            presets.forEachIndexed { index, (label, hex) ->
                val isSelected = currentColor.equals(hex, ignoreCase = true)
                val isFocused = inSubMenu && subFocusIndex % presets.size == index

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onColorSelect(hex) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(hex)))
                            .border(
                                width = if (isFocused) 3.5.dp else if (isSelected) 2.dp else 1.dp,
                                color = if (isFocused) CyanAccent else if (isSelected) TextWhite else CardBorder,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = label,
                        color = if (isFocused) CyanAccent else if (isSelected) TextWhite else TextDim,
                        fontSize = 12.sp,
                        fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun OrientationSection(
    currentOrientation: Int,
    inSubMenu: Boolean,
    subFocusIndex: Int,
    onOrientationSelect: (Int) -> Unit
) {
    Column {
        Text("设置掌机屏幕握持旋转策略：", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("AYN Odin 3 底侧为 USB 接口，仅支持固定横屏与传感器横屏两种模式。", color = TextDim, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(16.dp))

        val options = listOf(
            Pair("固定横屏（默认握持方向）", HardwareController.ORIENTATION_LANDSCAPE),
            Pair("传感器横屏（自适应正反横屏）", HardwareController.ORIENTATION_SENSOR_LANDSCAPE)
        )

        options.forEachIndexed { index, (label, mode) ->
            val isFocused = inSubMenu && subFocusIndex % options.size == index
            val isActive = currentOrientation == mode

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isFocused) CyanAccent.copy(alpha = 0.20f)
                        else if (isActive) DarkSurface
                        else CardBackground
                    )
                    .border(
                        width = if (isFocused) 2.dp else if (isActive) 1.dp else 0.dp,
                        color = if (isFocused) CyanAccent else if (isActive) CyanAccent.copy(alpha = 0.5f) else CardBorder,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onOrientationSelect(mode) }
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = if (isFocused || isActive) CyanAccent else TextWhite,
                        fontSize = 14.sp,
                        fontWeight = if (isFocused || isActive) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isActive) {
                        Text(
                            text = "✓ 当前生效",
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

@Composable
private fun AutoFanSection(
    autoFanControlEnabled: Boolean,
    socTemp: Float,
    inSubMenu: Boolean,
    subFocusIndex: Int,
    onToggleAutoFan: () -> Unit
) {
    Column {
        Text("自动风扇控制策略：", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("掌机在充电且未处于游戏状态，且 CPU & GPU 温度 <= 60°C 时将自动关闭风扇，保持安静。", color = TextDim, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(18.dp))

        val isFocused = inSubMenu && subFocusIndex == 0

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isFocused) CyanAccent.copy(alpha = 0.20f)
                    else if (autoFanControlEnabled) DarkSurface
                    else CardBackground
                )
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) CyanAccent else CardBorder,
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable { onToggleAutoFan() }
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "自动风扇控制",
                        color = if (isFocused) CyanAccent else TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (autoFanControlEnabled) "自动调度已开启 (按 A 键或点击切换)" else "自动调度已停用 (保留手动风扇档位)",
                        color = TextDim,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = if (autoFanControlEnabled) "✓ [ 开启 ]" else "✕ [ 关闭 ]",
                    color = if (autoFanControlEnabled) GreenActive else TextDim,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 实时状态监控与保护机制
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CardBackground)
                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("实时状态与温控保护监控", color = CyanAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("• 芯片实时最高温度 (SoC)：", color = TextWhite, fontSize = 13.sp)
                Text(
                    text = if (socTemp.isFinite()) "${"%.1f".format(socTemp)} °C" else "— °C",
                    color = if (!socTemp.isFinite()) TextDim else if (socTemp <= 60f) GreenActive else OrangeWarning,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("• 安全硬温控阈值：", color = TextWhite, fontSize = 13.sp)
                Text("60.0 °C (高于此温度强制散热)", color = OrangeWarning, fontSize = 13.sp)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("• 调度执行条件：", color = TextWhite, fontSize = 13.sp)
                Text("充电中 + 无游戏 + 温度 <= 60°C", color = TextDim, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TabEditSection(
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
    var newTabName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tab 分组管理与排序 (${tabs.size}/10)：", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("【上下键选Tab • 左右光标选中按钮 • A键确认】", color = CyanAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    placeholder = { Text("输入新 Tab 名称...", color = TextDim) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = CardBorder
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
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                ) {
                    Text("添加", color = PureBlack, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
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
                        .background(if (isRowFocused) CyanAccent.copy(alpha = 0.12f) else CardBackground)
                        .border(
                            width = if (isRowFocused) 2.dp else 1.dp,
                            color = if (isRowFocused) CyanAccent.copy(alpha = 0.7f) else CardBorder,
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
                            color = if (isRowFocused) CyanAccent else TextDim,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tab.name,
                            color = if (isRowFocused) CyanAccent else TextWhite,
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
                                    text = "★ 默认首页",
                                    color = PureBlack,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (tab.isGameTab) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CyanAccent.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "游戏分类",
                                    color = CyanAccent,
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
                                    .background(if (isBtnFocused) CyanAccent else DarkSurface)
                                    .border(
                                        width = if (isBtnFocused) 2.dp else 1.dp,
                                        color = if (isBtnFocused) CyanAccent else CardBorder,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onMoveTabUp(tab) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "▲ 上移",
                                    color = if (isBtnFocused) PureBlack else TextWhite,
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
                                    .background(if (isBtnFocused) CyanAccent else DarkSurface)
                                    .border(
                                        width = if (isBtnFocused) 2.dp else 1.dp,
                                        color = if (isBtnFocused) CyanAccent else CardBorder,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onMoveTabDown(tab) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "▼ 下移",
                                    color = if (isBtnFocused) PureBlack else TextWhite,
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
                                    .background(if (isBtnFocused) CyanAccent else CyanAccent.copy(alpha = 0.2f))
                                    .border(
                                        width = if (isBtnFocused) 2.dp else 1.dp,
                                        color = CyanAccent,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onSetDefaultTab(tab) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "设为首页",
                                    color = if (isBtnFocused) PureBlack else CyanAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 删除按钮
                        if (!tab.isDefault && tab.name != "全部应用") {
                            val isBtnFocused = focusedAction == com.odin.desktop.data.entity.TabAction.DELETE
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isBtnFocused) RedDanger else RedDanger.copy(alpha = 0.15f))
                                    .border(
                                        width = if (isBtnFocused) 2.dp else 1.dp,
                                        color = if (isBtnFocused) RedDanger else RedDanger.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onDeleteTab(tab) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "删除",
                                    color = if (isBtnFocused) PureBlack else RedDanger,
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

@Composable
private fun AboutSection() {
    Column {
        Text("Odin 3 专属掌机启动台", color = CyanAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Odin3 Desktop v1.0.0", color = TextDim, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "专为 AYN Odin 3 打造的极简高效掌机专属桌面与系统增强系统。\n" +
                    "• 纯黑 OLED 省电与防烧屏优化\n" +
                    "• 实体手柄全键位盲操适配\n" +
                    "• 充电风扇智能调度\n\n" +
                    "开源与技术组件致谢：\n" +
                    "- Android Jetpack & Compose\n" +
                    "- Room Persistence Library\n" +
                    "- Kotlin Coroutines\n",
            color = TextWhite,
            fontSize = 13.sp,
            lineHeight = 22.sp
        )
    }
}
