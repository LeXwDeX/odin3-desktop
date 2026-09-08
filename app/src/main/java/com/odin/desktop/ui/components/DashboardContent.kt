package com.odin.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.odin.desktop.R
import com.odin.desktop.dashboard.DashboardAction
import com.odin.desktop.dashboard.DashboardState
import com.odin.desktop.dashboard.ExternalStorageUsage
import com.odin.desktop.dashboard.MemoryUsage
import com.odin.desktop.dashboard.ProcessorUsage
import com.odin.desktop.dashboard.StorageUsage
import com.odin.desktop.dashboard.WifiUsage
import com.odin.desktop.ui.components.base.*
import com.odin.desktop.ui.theme.LocalOdinPalette
import com.odin.desktop.ui.theme.OdinDesktopTheme
import com.odin.desktop.ui.theme.OdinSizes
import com.odin.desktop.ui.theme.OdinSpacing
import com.odin.desktop.ui.theme.OdinTypography
import java.util.Locale

/** Dashboard selection belongs to the launcher; this view only paints it and handles touch. */
@Composable
fun DashboardContent(
    state: DashboardState,
    selectedControl: Int,
    hasFocus: Boolean,
    onAction: (DashboardAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalOdinPalette.current
    BoxWithConstraints(modifier.fillMaxSize().background(palette.background)) {
        val wide = maxWidth >= 620.dp
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = OdinSpacing.page, vertical = OdinSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(OdinSpacing.md)
        ) {
            if (wide) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(OdinSpacing.md)) {
                    StorageCards(state.storage, state.externalStorage, Modifier.weight(1.35f).fillMaxHeight())
                    MemoryCard(state.memory, state.loading, Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(OdinSpacing.md)) {
                    OdinEqualHeightRow(Modifier.weight(1.35f).fillMaxHeight()) {
                        ProcessorCard("CPU", state.cpu, Modifier.weight(1f).fillMaxHeight())
                        ProcessorCard("GPU", state.gpu, Modifier.weight(1f).fillMaxHeight())
                    }
                    WifiCard(state.wifi, state.loading, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                StorageCards(state.storage, state.externalStorage)
                MemoryCard(state.memory, state.loading, Modifier.fillMaxWidth())
                OdinEqualHeightRow {
                    ProcessorCard("CPU", state.cpu, Modifier.weight(1f).fillMaxHeight())
                    ProcessorCard("GPU", state.gpu, Modifier.weight(1f).fillMaxHeight())
                }
                WifiCard(state.wifi, state.loading, Modifier.fillMaxWidth())
            }
            DashboardAction.entries.chunked(if (wide) 3 else 2).forEach { actions ->
                OdinEqualHeightRow {
                    actions.forEach { action ->
                        DashboardActionControl(action, hasFocus && selectedControl == action.ordinal,
                            { onAction(action) }, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    OdinSurface(modifier, role = SurfaceRole.DENSE, content = content)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardActionControl(action: DashboardAction, focused: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused) { withFrameNanos { }; requester.bringIntoView() }
    }
    OdinControl(actionLabel(action), onClick, modifier.bringIntoViewRequester(requester), focused = focused,
        icon = { ActionIcon(action, Modifier.size(OdinSizes.icon)) })
}

@Composable
private fun CardTitle(title: String, trailing: String = "") {
    val palette = LocalOdinPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = palette.text, style = OdinTypography.caption, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(trailing, color = palette.textDim,
            style = OdinTypography.caption, maxLines = 1)
    }
}

/** Storage and RAM share the same reading order, baseline and capacity labels. */
@Composable
private fun CapacityHeading(title: String, used: Long?, total: Long?) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    CardTitle(title, strings.getString(R.string.text_total_value, formatBytes(total)))
    Row(Modifier.fillMaxWidth().padding(top = OdinSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(OdinSpacing.xs)) {
        Text(formatBytes(used), color = palette.text, style = OdinTypography.metric,
            modifier = Modifier.alignByBaseline(), maxLines = 1)
        Text(strings.getString(R.string.text_used_2), color = palette.textDim,
            style = OdinTypography.caption, modifier = Modifier.alignByBaseline())
    }
    Spacer(Modifier.height(OdinSpacing.sm))
}

@Composable
private fun StorageCards(internal: StorageUsage, external: List<ExternalStorageUsage>, modifier: Modifier = Modifier) {
    if (external.isEmpty()) {
        StorageCard(internal, modifier.fillMaxWidth())
    } else {
        // Each volume keeps its own capacity; extra volumes add rows rather than merging disks.
        Column(modifier, verticalArrangement = Arrangement.spacedBy(OdinSpacing.md)) {
            (0..external.size).chunked(2).forEach { indices ->
                OdinEqualHeightRow {
                    indices.forEach { index ->
                        if (index == 0) {
                            StorageCard(internal, Modifier.weight(1f).fillMaxHeight(), compact = true)
                        } else {
                            ExternalStorageCard(external[index - 1], Modifier.weight(1f).fillMaxHeight())
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
    val palette = LocalOdinPalette.current
    val categoryColors = listOf(palette.special, palette.accent, palette.warning, palette.storageFree)
    val strings = LocalContext.current
    val total = usage.totalBytes?.takeIf { it > 0 }
    val free = usage.freeBytes?.takeIf { it >= 0 }
    val used = if (total != null && free != null) (total - free).coerceIn(0, total) else null
    val categories = listOf(usage.systemBytes, usage.appsBytes, usage.otherBytes, usage.freeBytes)
    val complete = total != null && categories.all { it != null && it >= 0 }
    DashboardCard(modifier) {
        CapacityHeading(strings.getString(R.string.text_internal_storage), used, total)
        if (complete) {
            SegmentedBar(categories.map { it!!.toFloat() / total!!.toFloat() }, categoryColors)
        } else {
            // Only total/free are known yet: show aggregate use, never invent category proportions.
            UsageBar(if (used != null && total != null) used.toFloat() / total else null,
                palette.accent, remainingColor = palette.storageFree)
        }
        val categoryNames = listOf(strings.getString(R.string.text_system_2), strings.getString(R.string.text_apps), strings.getString(R.string.text_other), strings.getString(R.string.text_free))
        if (compact) {
            Column(Modifier.padding(top = OdinSpacing.sm), verticalArrangement = Arrangement.spacedBy(OdinSpacing.xs)) {
                categoryNames.indices.chunked(2).forEach { indices ->
                    Row(horizontalArrangement = Arrangement.spacedBy(OdinSpacing.sm)) {
                        indices.forEach { index ->
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Canvas(Modifier.size(OdinSpacing.xs)) { drawCircle(categoryColors[index]) }
                                Spacer(Modifier.width(OdinSpacing.xs))
                                Text(categoryNames[index], color = categoryColors[index], style = OdinTypography.caption)
                                Text(formatBytes(categories[index]), color = categoryColors[index], style = OdinTypography.caption,
                                    modifier = Modifier.weight(1f).padding(start = OdinSpacing.xs),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(top = OdinSpacing.sm), horizontalArrangement = Arrangement.spacedBy(OdinSpacing.sm)) {
                categoryNames.forEachIndexed { index, title ->
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(Modifier.size(OdinSpacing.xs)) { drawCircle(categoryColors[index]) }
                            Spacer(Modifier.width(OdinSpacing.xs))
                            Text(title, color = categoryColors[index], style = OdinTypography.caption)
                        }
                        Text(formatBytes(categories[index]), color = categoryColors[index], style = OdinTypography.caption,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        MetricNote(when {
            usage.needsUsageAccess -> strings.getString(R.string.text_app_storage_classification_permission_is_off)
            usage.note != null -> usage.note
            usage.loading -> strings.getString(R.string.text_reading_storage_categories)
            !complete -> strings.getString(R.string.text_categories_unavailable_bar_shows_total_usage)
            else -> null
        })
    }
}

@Composable
private fun ExternalStorageCard(usage: ExternalStorageUsage, modifier: Modifier) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    val total = usage.totalBytes?.takeIf { it > 0 }
    val free = usage.freeBytes?.takeIf { it >= 0 }
    val used = if (total != null && free != null) (total - free).coerceIn(0, total) else null
    DashboardCard(modifier) {
        CapacityHeading(usage.label.ifBlank { strings.getString(R.string.text_external_storage) }, used, total)
        UsageBar(if (used != null && total != null) used.toFloat() / total else null,
            palette.accent, remainingColor = palette.storageFree)
        Column(Modifier.padding(top = OdinSpacing.sm), verticalArrangement = Arrangement.spacedBy(OdinSpacing.xs)) {
            listOf(strings.getString(R.string.text_used_2) to used, strings.getString(R.string.text_free) to free).forEachIndexed { index, (title, bytes) ->
                val color = if (index == 0) palette.accent else palette.storageFree
                Row(Modifier.fillMaxWidth()) {
                    Text(title, color = color, style = OdinTypography.caption, modifier = Modifier.weight(1f))
                    Text(formatBytes(bytes), color = color, style = OdinTypography.caption, maxLines = 1)
                }
            }
        }
        MetricNote(if (usage.readOnly) strings.getString(R.string.text_read_only) else usage.note
            ?: if (total == null || free == null) strings.getString(R.string.text_capacity_unavailable) else null)
    }
}

@Composable
private fun MemoryCard(usage: MemoryUsage, loading: Boolean, modifier: Modifier) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    val total = usage.totalBytes?.takeIf { it > 0 }
    val used = usage.usedBytes?.takeIf { it >= 0 }
    DashboardCard(modifier) {
        CapacityHeading(strings.getString(R.string.text_memory), used, total)
        UsageBar(if (used != null && total != null) used.toFloat() / total else null, palette.accent)
        Row(Modifier.fillMaxWidth().padding(top = OdinSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(strings.getString(R.string.text_non_system_apps), color = palette.textDim, style = OdinTypography.caption, modifier = Modifier.weight(1f))
            Text(formatBytes(usage.nonSystemAppBytes), color = palette.text, style = OdinTypography.body)
        }
        MetricNote(usage.note ?: if (loading) strings.getString(R.string.text_reading_memory) else null)
    }
}

@Composable
private fun ProcessorCard(title: String, usage: ProcessorUsage, modifier: Modifier) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    val temperature = usage.temperatureC?.takeIf { it.isFinite() }
    val barColor = when {
        temperature == null || temperature < 60f -> palette.accent
        temperature < 80f -> palette.warning
        else -> palette.danger
    }
    DashboardCard(modifier) {
        CardTitle(strings.getString(R.string.text_value_temperature, title))
        Text(temperature?.let { String.format(Locale.getDefault(), "%.0f °C", it) } ?: "— °C",
            color = palette.text, style = OdinTypography.metric, modifier = Modifier.padding(top = OdinSpacing.xs))
        // The scale and colors are visual guides, not OEM thermal policy thresholds.
        UsageBar(temperature?.let { it / 105f }, barColor, Modifier.padding(top = OdinSpacing.xs))
        Row(Modifier.fillMaxWidth().padding(top = OdinSpacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0°", color = palette.textDim, style = OdinTypography.caption)
            Text("105°", color = palette.textDim, style = OdinTypography.caption)
        }
    }
}

@Composable
private fun WifiCard(usage: WifiUsage, loading: Boolean, modifier: Modifier) {
    val palette = LocalOdinPalette.current
    val strings = LocalContext.current
    val name = when {
        usage.ssid != null -> usage.ssid
        loading -> strings.getString(R.string.text_reading)
        usage.connected -> strings.getString(R.string.text_wi_fi_connected)
        else -> strings.getString(R.string.text_wi_fi_disconnected_2)
    }
    DashboardCard(modifier) {
        CardTitle("Wi-Fi", if (usage.connected) strings.getString(R.string.text_connected) else strings.getString(R.string.text_offline))
        Text(name, color = palette.text, style = OdinTypography.body, modifier = Modifier.padding(top = OdinSpacing.xs), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth().padding(top = OdinSpacing.xs), horizontalArrangement = Arrangement.spacedBy(OdinSpacing.md)) {
            Text("↑ ${formatRate(usage.txBytesPerSecond)}", color = palette.accent, style = OdinTypography.caption)
            Text("↓ ${formatRate(usage.rxBytesPerSecond)}", color = palette.text, style = OdinTypography.caption)
        }
        MetricNote(usage.note ?: if (usage.needsLocationAccess) strings.getString(R.string.text_wi_fi_name_permission_is_off) else null)
    }
}

@Composable
private fun MetricNote(note: String?) {
    val palette = LocalOdinPalette.current
    if (!note.isNullOrBlank()) {
        Text(note, color = palette.textDim, style = OdinTypography.caption,
            modifier = Modifier.padding(top = OdinSpacing.xs), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun UsageBar(fraction: Float?, color: Color, modifier: Modifier = Modifier,
    remainingColor: Color = LocalOdinPalette.current.track) {
    val fractions = fraction?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)?.let { listOf(it, 1f - it) }
    OdinBar(fractions, listOf(color, remainingColor), modifier)
}

@Composable
private fun SegmentedBar(fractions: List<Float>, colors: List<Color>, modifier: Modifier = Modifier) = OdinBar(fractions, colors, modifier)

private fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var amount = bytes.toDouble()
    var unit = 0
    while (amount >= 1024.0 && unit < units.lastIndex) { amount /= 1024.0; unit++ }
    return String.format(Locale.getDefault(), if (unit <= 1) "%.0f %s" else "%.1f %s", amount, units[unit])
}

private fun formatRate(bytes: Long?): String = if (bytes == null || bytes < 0) "—" else "${formatBytes(bytes)}/s"

@Composable
private fun actionLabel(action: DashboardAction): String {
    val strings = LocalContext.current
    return when (action) {
    DashboardAction.FILES -> strings.getString(R.string.text_files)
    DashboardAction.SYSTEM_SETTINGS -> strings.getString(R.string.text_system_settings)
    DashboardAction.ODIN_SETTINGS -> strings.getString(R.string.text_odin_settings)
}
}


@Composable
private fun ActionIcon(action: DashboardAction, modifier: Modifier) {
    val palette = LocalOdinPalette.current
    Canvas(modifier) {
        val stroke = 1.5.dp.toPx()
        val w = size.width
        val h = size.height
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(palette.accent, Offset(x1 * w, y1 * h), Offset(x2 * w, y2 * h), stroke, StrokeCap.Round)
        when (action) {
            DashboardAction.FILES -> {
                val folder = Path().apply {
                    moveTo(w * .1f, h * .22f); lineTo(w * .43f, h * .22f)
                    lineTo(w * .55f, h * .36f); lineTo(w * .9f, h * .36f)
                    lineTo(w * .9f, h * .82f); lineTo(w * .1f, h * .82f); close()
                }
                drawPath(folder, palette.accent, style = Stroke(stroke))
            }
            DashboardAction.SYSTEM_SETTINGS -> {
                listOf(.23f, .5f, .77f).forEachIndexed { index, y ->
                    line(.1f, y, .9f, y)
                    drawCircle(palette.surface, stroke * 1.9f, Offset(w * (if (index == 1) .66f else .34f), h * y))
                    drawCircle(palette.accent, stroke * 1.5f, Offset(w * (if (index == 1) .66f else .34f), h * y), style = Stroke(stroke))
                }
            }
            DashboardAction.ODIN_SETTINGS -> {
                listOf(.12f, .58f).forEach { x -> listOf(.12f, .58f).forEach { y ->
                    drawRoundRect(palette.accent, Offset(w * x, h * y), Size(w * .3f, h * .3f),
                        CornerRadius(stroke), style = Stroke(stroke))
                } }
            }
        }
    }
}

@Preview(name = "Dashboard · Internal storage", widthDp = 833, heightDp = 350)
@Composable
private fun InternalStorageDashboardPreview() {
    OdinDesktopTheme {
        DashboardContent(previewDashboardState(), selectedControl = 0, hasFocus = true, onAction = {})
    }
}

@Preview(name = "Dashboard · Internal and external storage", widthDp = 833, heightDp = 350)
@Composable
private fun ExternalStorageDashboardPreview() {
    OdinDesktopTheme {
        DashboardContent(previewDashboardState(withExternal = true), selectedControl = 0, hasFocus = true, onAction = {})
    }
}

/** Synthetic preview data, with no device or repository access. */
@Composable
private fun previewDashboardState(withExternal: Boolean = false): DashboardState {
    val strings = LocalContext.current
    val gib = 1024L * 1024 * 1024
    return DashboardState(
        storage = StorageUsage(totalBytes = 512 * gib, freeBytes = 300 * gib,
            systemBytes = 40 * gib, appsBytes = 150 * gib, otherBytes = 22 * gib, loading = false),
        externalStorage = if (withExternal) listOf(
            ExternalStorageUsage("preview-sd", strings.getString(R.string.text_sd_card), totalBytes = 256 * gib, freeBytes = 100 * gib)
        ) else emptyList(),
        memory = MemoryUsage(totalBytes = 16 * gib, usedBytes = 6 * gib, nonSystemAppBytes = 3 * gib),
        cpu = ProcessorUsage(temperatureC = 41f),
        gpu = ProcessorUsage(temperatureC = 39f),
        wifi = WifiUsage(connected = true, ssid = strings.getString(R.string.text_example_wi_fi), rxBytesPerSecond = 1_250_000, txBytesPerSecond = 800_000),
        loading = false
    )
}
