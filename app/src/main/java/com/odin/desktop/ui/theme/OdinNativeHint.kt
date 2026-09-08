package com.odin.desktop.ui.theme

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.TextView
import android.util.TypedValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.widget.TextViewCompat
import kotlin.math.roundToInt

/** Native overlay adapter: the service consumes the same type, spacing and surface tokens. */
fun TextView.applyOdinHintStyle() {
    val palette = OdinPalette()
    val metrics = resources.displayMetrics
    setTextColor(palette.text.toArgb())
    background = GradientDrawable().apply {
        setColor(palette.surface.toArgb())
        cornerRadius = OdinCorners.card.value * metrics.density
    }
    val inset = (OdinSpacing.card.value * metrics.density).roundToInt()
    setPadding(inset, inset, inset, inset)
    textSize = OdinTypography.body.fontSize.value
    includeFontPadding = false
    typeface = Typeface.create(Typeface.DEFAULT, OdinTypography.body.fontWeight!!.weight, false)
    TextViewCompat.setLineHeight(this, TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, OdinTypography.body.lineHeight.value, metrics).roundToInt())
    gravity = Gravity.CENTER
}
