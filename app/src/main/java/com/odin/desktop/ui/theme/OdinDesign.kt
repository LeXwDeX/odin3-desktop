package com.odin.desktop.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object OdinTypography {
    private fun text(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = size.sp,
        lineHeight = height.sp,
        fontWeight = weight,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    val h1 = text(22, 28, FontWeight.Bold)
    val h2 = text(16, 24, FontWeight.SemiBold)
    val body = text(14, 20, FontWeight.Medium)
    val caption = text(12, 16)
    val metric = text(24, 28, FontWeight.SemiBold)

    val material = Typography(
        headlineLarge = h1,
        headlineMedium = h1,
        headlineSmall = h2,
        titleLarge = h2,
        titleMedium = body,
        titleSmall = body,
        bodyLarge = body,
        bodyMedium = body,
        bodySmall = caption,
        labelLarge = body,
        labelMedium = caption,
        labelSmall = caption
    )
}

object OdinSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val page = xl
    val panel = xl
    val card = lg
    val denseCard = md
}

object OdinCorners {
    val badge = 4.dp
    val control = 8.dp
    val card = 12.dp
    val dialog = 16.dp
}

/** Reserved chrome and its rendered surface share the same dimensions. */
object OdinSizes {
    val controlHeight = 44.dp
    @Composable
    fun scaledControlHeight() = maxOf(controlHeight,
        with(LocalDensity.current) { OdinTypography.body.lineHeight.toDp() } + OdinSpacing.xl)
    @Composable
    fun chromeHeight() = scaledControlHeight() + OdinSpacing.sm * 2
    @Composable
    fun readoutLineHeight() = with(LocalDensity.current) { OdinTypography.h2.lineHeight.toDp() }
    @Composable
    fun headerHeight() = maxOf(scaledControlHeight(), readoutLineHeight() * 2) + OdinSpacing.sm * 2
    @Composable
    fun tagHeight() = with(LocalDensity.current) { OdinTypography.body.lineHeight.toDp() } + OdinSpacing.sm
    val icon = 24.dp
    val imageIcon = 28.dp
    val barHeight = 8.dp
    val fieldActionWidth = 80.dp
}

val OdinMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(OdinCorners.badge),
    small = RoundedCornerShape(OdinCorners.control),
    medium = RoundedCornerShape(OdinCorners.card),
    large = RoundedCornerShape(OdinCorners.dialog),
    extraLarge = RoundedCornerShape(OdinCorners.dialog)
)

/** Component spacing is semantic: change one family without resizing unrelated elements. */
object OdinInsets {
    val control = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    val iconControl = PaddingValues(8.dp)
    val badge = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
}
