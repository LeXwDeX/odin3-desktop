package com.odin.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.odin.desktop.dashboard.DashboardAction
import com.odin.desktop.dashboard.DashboardState
import com.odin.desktop.dashboard.ExternalStorageUsage
import com.odin.desktop.dashboard.MemoryUsage
import com.odin.desktop.dashboard.ProcessorUsage
import com.odin.desktop.dashboard.StorageUsage
import com.odin.desktop.dashboard.WifiUsage
import com.odin.desktop.ui.theme.CardBorder
import com.odin.desktop.ui.theme.CyanAccent
import com.odin.desktop.ui.theme.DarkSurface
import com.odin.desktop.ui.theme.PureBlack
import com.odin.desktop.ui.theme.OdinDesktopTheme
import com.odin.desktop.ui.theme.TextDim
import com.odin.desktop.ui.theme.TextWhite
import java.util.Locale

private val StorageColors = listOf(Color(0xFF758CA7), CyanAccent, Color(0xFF527B86), Color(0xFF26343A))

/** Dashboard selection belongs to the launcher; this view only paints it and handles touch. */
@Composable
fun DashboardContent(
    state: DashboardState,
    selectedControl: Int,
    hasFocus: Boolean,
    onAction: (DashboardAction) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize().background(PureBlack)) {
        val wide = maxWidth >= 620.dp
        val storageRows = (state.externalStorage.size + 2) / 2
        val storageHeight = 136.dp * storageRows + 10.dp * (storageRows - 1)
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StorageCards(state.storage, state.externalStorage)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ProcessorCard("CPU", state.cpu, Modifier.weight(1f).height(104.dp))
                            ProcessorCard("GPU", state.gpu, Modifier.weight(1f).height(104.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MemoryCard(state.memory, state.loading, Modifier.fillMaxWidth().height(storageHeight))
                        WifiCard(state.wifi, state.loading, Modifier.fillMaxWidth().height(104.dp))
                    }
                }
            } else {
                StorageCards(state.storage, state.externalStorage)
                MemoryCard(state.memory, state.loading, Modifier.fillMaxWidth().height(136.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProcessorCard("CPU", state.cpu, Modifier.weight(1f).height(104.dp))
                    ProcessorCard("GPU", state.gpu, Modifier.weight(1f).height(104.dp))
                }
                WifiCard(state.wifi, state.loading, Modifier.fillMaxWidth().height(104.dp))
            }
            val actions = DashboardAction.entries
            actions.chunked(if (wide) 4 else 2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { action ->
                        DashboardCard(
                            modifier = Modifier.weight(1f).height(64.dp),
                            selected = hasFocus && selectedControl == action.ordinal,
                            onClick = { onAction(action) }
                        ) {
                            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                                ActionIcon(action, Modifier.size(18.dp))
                                Text(actionLabel(action), color = TextWhite, fontSize = 12.sp, lineHeight = 16.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) {
        if (selected) {
            withFrameNanos { }
            requester.bringIntoView()
        }
    }
    val touch = if (onClick != null) Modifier.focusProperties { canFocus = false }
        .clickable(role = Role.Button, onClick = onClick) else Modifier
    ProvideTextStyle(TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))) {
        Column(
            modifier.bringIntoViewRequester(requester).clip(shape)
                .background(if (selected) Color(0xFF092127) else DarkSurface)
                .border(if (selected) 2.dp else 1.dp, if (selected) CyanAccent else CardBorder, shape)
                .then(touch).padding(12.dp),
            content = content
        )
    }
}

@Composable
private fun CardTitle(title: String, trailing: String = "") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextWhite, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(trailing, color = TextDim,
            fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1)
    }
}

@Composable
private fun StorageCards(internal: StorageUsage, external: List<ExternalStorageUsage>) {
    if (external.isEmpty()) {
        StorageCard(internal, Modifier.fillMaxWidth().height(136.dp))
    } else {
        // Each volume keeps its own capacity; extra volumes add rows rather than merging disks.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (0..external.size).chunked(2).forEach { indices ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    indices.forEach { index ->
                        if (index == 0) {
                            StorageCard(internal, Modifier.weight(1f).height(136.dp), compact = true)
                        } else {
                            ExternalStorageCard(external[index - 1], Modifier.weight(1f).height(136.dp))
                        }
                    }
                    if (indices.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StorageCard(usage: StorageUsage, modifier: Modifier, compact: Boolean = false) {
    val total = usage.totalBytes?.takeIf { it > 0 }
    val free = usage.freeBytes?.takeIf { it >= 0 }
    val used = if (total != null && free != null) (total - free).coerceIn(0, total) else null
    val categories = listOf(usage.systemBytes, usage.appsBytes, usage.otherBytes, usage.freeBytes)
    val complete = total != null && categories.all { it != null && it >= 0 }
    DashboardCard(modifier) {
        CardTitle("内部存储", if (compact) "共 ${formatBytes(total)}" else if (usage.loading) "统计中" else "")
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.Bottom) {
            Text(formatBytes(used), color = TextWhite, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
            Text(" 已用", color = TextDim, fontSize = 10.sp, lineHeight = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
            if (!compact) {
                Spacer(Modifier.weight(1f))
                Text("共 ${formatBytes(total)}", color = TextDim, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        Spacer(Modifier.height(7.dp))
        if (complete) {
            SegmentedBar(categories.map { it!!.toFloat() / total!!.toFloat() }, StorageColors)
        } else {
            // Only total/free are known yet: show aggregate use, never invent category proportions.
            UsageBar(if (used != null && total != null) used.toFloat() / total else null, Color(0xFF527B86))
        }
        val categoryNames = listOf("系统", "应用", "其他", "空闲")
        if (compact) {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                categoryNames.indices.chunked(2).forEach { indices ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        indices.forEach { index ->
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Text(categoryNames[index], color = TextDim, fontSize = 10.sp, lineHeight = 14.sp)
                                Text(formatBytes(categories[index]), color = TextWhite, fontSize = 10.sp, lineHeight = 14.sp,
                                    modifier = Modifier.weight(1f).padding(start = 3.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categoryNames.forEachIndexed { index, title ->
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(Modifier.size(5.dp)) { drawCircle(StorageColors[index]) }
                            Spacer(Modifier.width(4.dp))
                            Text(title, color = TextDim, fontSize = 10.sp, lineHeight = 12.sp)
                        }
                        Text(formatBytes(categories[index]), color = TextWhite, fontSize = 11.sp, lineHeight = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        MetricNote(when {
            usage.needsUsageAccess -> "应用分类统计权限未开启"
            usage.note != null -> usage.note
            usage.loading -> "正在读取存储分类…"
            !complete -> "分类数据暂不可读取 · 条形显示总用量"
            else -> null
        })
    }
}

@Composable
private fun ExternalStorageCard(usage: ExternalStorageUsage, modifier: Modifier) {
    val total = usage.totalBytes?.takeIf { it > 0 }
    val free = usage.freeBytes?.takeIf { it >= 0 }
    val used = if (total != null && free != null) (total - free).coerceIn(0, total) else null
    DashboardCard(modifier) {
        CardTitle(usage.label.ifBlank { "外部存储" }, if (usage.readOnly) "只读" else "")
        Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.Bottom) {
            Text(formatBytes(total), color = TextWhite, fontSize = 21.sp, lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(" 总量", color = TextDim, fontSize = 10.sp, lineHeight = 12.sp,
                modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.height(7.dp))
        UsageBar(if (used != null && total != null) used.toFloat() / total else null, CyanAccent)
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("已用" to used, "空闲" to free).forEach { (title, bytes) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(title, color = TextDim, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.weight(1f))
                    Text(formatBytes(bytes), color = TextWhite, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1)
                }
            }
        }
        MetricNote(usage.note ?: if (total == null || free == null) "容量暂不可读取" else null)
    }
}

@Composable
private fun MemoryCard(usage: MemoryUsage, loading: Boolean, modifier: Modifier) {
    val total = usage.totalBytes?.takeIf { it > 0 }
    val used = usage.usedBytes?.takeIf { it >= 0 }
    DashboardCard(modifier) {
        CardTitle("运行内存", "RAM")
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.Bottom) {
            Text(formatBytes(used), color = TextWhite, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
            Text(" / ${formatBytes(total)}", color = TextDim, fontSize = 12.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.height(7.dp))
        UsageBar(if (used != null && total != null) used.toFloat() / total else null, CyanAccent)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("非系统应用", color = TextDim, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.weight(1f))
            Text(formatBytes(usage.nonSystemAppBytes), color = TextWhite, fontSize = 15.sp, lineHeight = 19.sp,
                fontWeight = FontWeight.Medium)
        }
        MetricNote(usage.note ?: if (loading) "正在读取内存…" else null)
    }
}

@Composable
private fun ProcessorCard(title: String, usage: ProcessorUsage, modifier: Modifier) {
    val temperature = usage.temperatureC?.takeIf { it.isFinite() }
    val barColor = when {
        temperature == null || temperature < 60f -> CyanAccent
        temperature < 80f -> Color(0xFFFFB454)
        else -> Color(0xFFFF6262)
    }
    DashboardCard(modifier) {
        CardTitle("$title 温度")
        Text(temperature?.let { String.format(Locale.getDefault(), "%.0f °C", it) } ?: "— °C",
            color = TextWhite, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp))
        // The scale and colors are visual guides, not OEM thermal policy thresholds.
        Canvas(Modifier.fillMaxWidth().padding(top = 3.dp).height(5.dp).clip(RoundedCornerShape(3.dp))) {
            drawRect(Color(0xFF26343A))
            temperature?.let {
                drawRect(barColor, size = Size(size.width * (it / 105f).coerceIn(0f, 1f), size.height))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0°", color = TextDim, fontSize = 9.sp, lineHeight = 12.sp)
            Text("105°", color = TextDim, fontSize = 9.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun WifiCard(usage: WifiUsage, loading: Boolean, modifier: Modifier) {
    val name = when {
        usage.ssid != null -> usage.ssid
        loading -> "正在读取…"
        usage.connected -> "已连接 Wi-Fi"
        else -> "未连接 Wi-Fi"
    }
    DashboardCard(modifier) {
        CardTitle("Wi-Fi", if (usage.connected) "已连接" else "未连接")
        Text(name, color = TextWhite, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("↑ ${formatRate(usage.txBytesPerSecond)}", color = CyanAccent, fontSize = 11.sp, lineHeight = 14.sp)
            Text("↓ ${formatRate(usage.rxBytesPerSecond)}", color = TextWhite, fontSize = 11.sp, lineHeight = 14.sp)
        }
        MetricNote(usage.note ?: if (usage.needsLocationAccess) "Wi-Fi 名称读取权限未开启" else null)
    }
}

@Composable
private fun MetricNote(note: String?) {
    if (!note.isNullOrBlank()) {
        Text(note, color = TextDim, fontSize = 9.sp, lineHeight = 11.sp,
            modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun UsageBar(fraction: Float?, color: Color, modifier: Modifier = Modifier) {
    if (fraction != null && fraction.isFinite()) {
        SegmentedBar(listOf(fraction.coerceIn(0f, 1f), (1f - fraction).coerceIn(0f, 1f)),
            listOf(color, Color(0xFF26343A)), modifier)
    } else {
        Canvas(modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))) {
            drawRect(Color(0xFF26343A))
            // A striped placeholder distinguishes an unavailable measurement from zero usage.
            var x = -size.height
            while (x < size.width) {
                drawLine(CardBorder, Offset(x, size.height), Offset(x + size.height, 0f), 2.dp.toPx())
                x += 10.dp.toPx()
            }
        }
    }
}

@Composable
private fun SegmentedBar(fractions: List<Float>, colors: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))) {
        drawRect(Color(0xFF26343A))
        var offset = 0f
        fractions.forEachIndexed { index, value ->
            val width = (value.coerceIn(0f, 1f) * size.width).coerceAtMost(size.width - offset)
            if (width > 0f) drawRect(colors[index], Offset(offset, 0f), Size(width, size.height))
            offset += width
        }
    }
}

private fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var amount = bytes.toDouble()
    var unit = 0
    while (amount >= 1024.0 && unit < units.lastIndex) { amount /= 1024.0; unit++ }
    return String.format(Locale.getDefault(), if (unit <= 1) "%.0f %s" else "%.1f %s", amount, units[unit])
}

private fun formatRate(bytes: Long?): String = if (bytes == null || bytes < 0) "—" else "${formatBytes(bytes)}/s"

private fun actionLabel(action: DashboardAction): String = when (action) {
    DashboardAction.FILES -> "文件管理"
    DashboardAction.SYSTEM_SETTINGS -> "系统设置"
    DashboardAction.ODIN_SETTINGS -> "Odin 设置"
    DashboardAction.FILTERS -> "滤镜调整"
}

@Composable
private fun ActionIcon(action: DashboardAction, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 1.5.dp.toPx()
        val w = size.width
        val h = size.height
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(CyanAccent, Offset(x1 * w, y1 * h), Offset(x2 * w, y2 * h), stroke, StrokeCap.Round)
        when (action) {
            DashboardAction.FILES -> {
                val folder = Path().apply {
                    moveTo(w * .1f, h * .22f); lineTo(w * .43f, h * .22f)
                    lineTo(w * .55f, h * .36f); lineTo(w * .9f, h * .36f)
                    lineTo(w * .9f, h * .82f); lineTo(w * .1f, h * .82f); close()
                }
                drawPath(folder, CyanAccent, style = Stroke(stroke))
            }
            DashboardAction.SYSTEM_SETTINGS -> {
                listOf(.23f, .5f, .77f).forEachIndexed { index, y ->
                    line(.1f, y, .9f, y)
                    drawCircle(DarkSurface, stroke * 1.9f, Offset(w * (if (index == 1) .66f else .34f), h * y))
                    drawCircle(CyanAccent, stroke * 1.5f, Offset(w * (if (index == 1) .66f else .34f), h * y), style = Stroke(stroke))
                }
            }
            DashboardAction.ODIN_SETTINGS -> {
                listOf(.12f, .58f).forEach { x -> listOf(.12f, .58f).forEach { y ->
                    drawRoundRect(CyanAccent, Offset(w * x, h * y), Size(w * .3f, h * .3f),
                        CornerRadius(stroke), style = Stroke(stroke))
                } }
            }
            else -> listOf(.22f, .5f, .78f).forEachIndexed { index, y ->
                drawLine(CyanAccent.copy(alpha = 1f - index * .25f), Offset(w * .1f, h * y),
                    Offset(w * .9f, h * y), stroke, StrokeCap.Round)
            }
        }
    }
}

@Preview(name = "Dashboard · 仅内置存储", widthDp = 833, heightDp = 350)
@Composable
private fun InternalStorageDashboardPreview() {
    OdinDesktopTheme {
        DashboardContent(previewDashboardState(), selectedControl = 0, hasFocus = true, onAction = {})
    }
}

@Preview(name = "Dashboard · 内置和外部存储", widthDp = 833, heightDp = 350)
@Composable
private fun ExternalStorageDashboardPreview() {
    OdinDesktopTheme {
        DashboardContent(previewDashboardState(withExternal = true), selectedControl = 0, hasFocus = true, onAction = {})
    }
}

/** Synthetic preview data, with no device or repository access. */
private fun previewDashboardState(withExternal: Boolean = false): DashboardState {
    val gib = 1024L * 1024 * 1024
    return DashboardState(
        storage = StorageUsage(totalBytes = 512 * gib, freeBytes = 300 * gib,
            systemBytes = 40 * gib, appsBytes = 150 * gib, otherBytes = 22 * gib, loading = false),
        externalStorage = if (withExternal) listOf(
            ExternalStorageUsage("preview-sd", "SD 卡", totalBytes = 256 * gib, freeBytes = 100 * gib)
        ) else emptyList(),
        memory = MemoryUsage(totalBytes = 16 * gib, usedBytes = 6 * gib, nonSystemAppBytes = 3 * gib),
        cpu = ProcessorUsage(temperatureC = 41f),
        gpu = ProcessorUsage(temperatureC = 39f),
        wifi = WifiUsage(connected = true, ssid = "示例 Wi-Fi", rxBytesPerSecond = 1_250_000, txBytesPerSecond = 800_000),
        loading = false
    )
}
