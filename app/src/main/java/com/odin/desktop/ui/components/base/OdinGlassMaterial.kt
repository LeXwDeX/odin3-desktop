package com.odin.desktop.ui.components.base

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.odin.desktop.ui.theme.OdinPalette

/** Shared dim layer for dialogs above translucent surfaces. */
internal const val OdinGlassScrimOpacity = .88f

/** One material formula: icons show more background; controls include text fields; reading surfaces are deeper. */
internal enum class GlassRole(val opacity: Float, val light: Float, val edge: Float) {
    ICON(.66f, .10f, .20f),
    CONTROL(.80f, .07f, .16f),
    CARD(.79f, .08f, .16f),
    DENSE(.83f, .07f, .16f),
    PANEL(.89f, .055f, .13f),
    NAVIGATION(.85f, .06f, .15f),
    MODAL(.93f, .05f, .14f),
    BADGE(.78f, .06f, .15f)
}

/** Cached translucent material. This draws no backdrop blur; content remains sharp. */
internal fun Modifier.odinGlassSurface(
    palette: OdinPalette,
    role: GlassRole,
    radius: Dp,
    emphasized: Boolean = false,
    tint: Color? = null
): Modifier = drawWithCache {
    val activeBoost = if (emphasized) .07f else 0f
    val surfaceBase = if (emphasized) palette.selection else when (role) {
        GlassRole.PANEL, GlassRole.NAVIGATION, GlassRole.MODAL -> palette.surface
        else -> palette.card
    }
    val base = tint?.let { lerp(surfaceBase, it, if (role == GlassRole.BADGE) .18f else .30f) }
        ?: surfaceBase
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val fill = Brush.linearGradient(
        colors = listOf(
            lerp(base, Color(0xFF244668), .38f)
                .copy(alpha = (role.opacity + .02f + activeBoost).coerceAtMost(.96f)),
            lerp(base, Color(0xFF193351), .35f)
                .copy(alpha = (role.opacity - .06f + activeBoost).coerceAtMost(.96f)),
            lerp(base, Color(0xFF0D203A), .35f)
                .copy(alpha = (role.opacity + .04f + activeBoost).coerceAtMost(.96f))
        ),
        start = Offset.Zero,
        end = Offset(w, h)
    )
    val sideLight = Brush.linearGradient(
        colors = listOf(
            palette.focus.copy(alpha = role.light + (if (emphasized) .04f else 0f)),
            Color(0x0C80B8EB),
            Color.Transparent
        ),
        start = Offset.Zero,
        end = Offset(w * .90f, h * .95f)
    )
    val edge = Brush.linearGradient(
        colors = listOf(
            palette.focus.copy(alpha = role.edge + (if (emphasized) .08f else 0f)),
            palette.focus.copy(alpha = .09f),
            palette.focus.copy(alpha = .13f)
        ),
        start = Offset.Zero,
        end = Offset(w, h)
    )
    val stroke = Stroke(1.dp.toPx())
    val inset = stroke.width / 2f
    val edgeSize = Size(
        (size.width - 2f * inset).coerceAtLeast(0f),
        (size.height - 2f * inset).coerceAtLeast(0f)
    )
    val fillCorner = CornerRadius(radius.toPx().coerceAtLeast(0f))
    val edgeCorner = CornerRadius((radius.toPx() - inset).coerceAtLeast(0f))

    onDrawBehind {
        if (size.width <= 0f || size.height <= 0f) return@onDrawBehind
        drawRoundRect(brush = fill, cornerRadius = fillCorner)
        drawRoundRect(brush = sideLight, cornerRadius = fillCorner)
        drawRoundRect(
            brush = edge,
            topLeft = Offset(inset, inset),
            size = edgeSize,
            cornerRadius = edgeCorner,
            style = stroke
        )
    }
}
