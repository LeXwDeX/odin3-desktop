package com.odin.desktop.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object OdinTypography {
    private fun text(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = size.sp,
        lineHeight = height.sp,
        fontWeight = weight,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    val h1 = text(22, 28, FontWeight.Bold)
    val h2 = text(16, 24, FontWeight.SemiBold)
    val h3 = text(14, 20, FontWeight.Medium)
    val body = text(12, 18)
    val input = text(14, 20)
    val caption = text(11, 16)
    val micro = text(10, 14)
    val button = text(12, 18, FontWeight.Medium)

    // Telemetry uses a compact scale so comparable values fit the same fixed-size cards.
    val dataValue = text(20, 26, FontWeight.SemiBold)
    val dataLarge = text(28, 34, FontWeight.SemiBold)
    val dataLabel = text(12, 16, FontWeight.Medium)
    val dataCaption = text(11, 14)
    val dataNote = text(10, 12)
    val symbol = text(48, 56)

    val material = Typography(
        headlineLarge = h1,
        headlineMedium = h1,
        headlineSmall = h2,
        titleLarge = h2,
        titleMedium = h3,
        titleSmall = h3,
        bodyLarge = input,
        bodyMedium = body,
        bodySmall = caption,
        labelLarge = button,
        labelMedium = caption,
        labelSmall = micro
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
    val headerHeight = 64.dp
    val dockHeight = 64.dp
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
    val button = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp)
    val compactButton = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)
    val optionRow = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp)
    val tab = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 8.dp)
    val dockControl = PaddingValues(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 4.dp)
    val badge = PaddingValues(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 4.dp)
}
