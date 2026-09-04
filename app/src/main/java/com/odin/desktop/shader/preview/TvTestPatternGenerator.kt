package com.odin.desktop.shader.preview

import com.odin.desktop.R
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.sin

/**
 * 电视游戏/广播级屏幕校准测试图生成器。
 * 生成标准 1920x1080 SMPTE 彩条图、几何网格图与复古游戏测试画面，
 * 供用户在进入游戏前或调参时作为专业电视标定画面。
 */
object TvTestPatternGenerator {

    enum class PatternType(@androidx.annotation.StringRes val displayNameRes: Int) {
        SMPTE_COLOR_BARS(R.string.text_smpte_color_bars),
        CROSSHATCH_GRID(R.string.text_alignment_grid),
        RETRO_PIXEL_SCENE(R.string.text_retro_pixel_game_scene)
    }

    fun generate(type: PatternType, width: Int = 1920, height: Int = 1080): Bitmap {
        return when (type) {
            PatternType.SMPTE_COLOR_BARS -> createSmptePattern(width, height)
            PatternType.CROSSHATCH_GRID -> createCrosshatchPattern(width, height)
            PatternType.RETRO_PIXEL_SCENE -> createRetroPixelPattern(width, height)
        }
    }

    /**
     * 生成标准 SMPTE 电视彩条信号图 (带 16 级灰阶梯级、PLUGE 脉冲与中心校准环)
     */
    private fun createSmptePattern(w: Int, h: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. 顶层 67% 高度：7 种 SMPTE 标准 75% 饱和度彩条
        val topColors = intArrayOf(
            Color.rgb(192, 192, 192), // 灰色 / 白
            Color.rgb(192, 192, 0),   // 黄
            Color.rgb(0, 192, 192),   // 青
            Color.rgb(0, 192, 0),     // 绿
            Color.rgb(192, 0, 192),   // 品红
            Color.rgb(192, 0, 0),     // 红
            Color.rgb(0, 0, 192)      // 蓝
        )
        val topH = (h * 0.67f).toInt()
        val barW = w / 7f
        for (i in 0 until 7) {
            paint.color = topColors[i]
            canvas.drawRect(i * barW, 0f, (i + 1) * barW, topH.toFloat(), paint)
        }

        // 2. 中层 8% 高度：交错反转彩条 (Castellation)
        val midColors = intArrayOf(
            Color.rgb(0, 0, 192),     // 蓝
            Color.rgb(19, 19, 19),    // 黑
            Color.rgb(192, 0, 192),   // 品红
            Color.rgb(19, 19, 19),    // 黑
            Color.rgb(0, 192, 192),   // 青
            Color.rgb(19, 19, 19),    // 黑
            Color.rgb(192, 192, 192)  // 白
        )
        val midH = (h * 0.08f).toInt()
        for (i in 0 until 7) {
            paint.color = midColors[i]
            canvas.drawRect(i * barW, topH.toFloat(), (i + 1) * barW, (topH + midH).toFloat(), paint)
        }

        // 3. 底层 25% 高度：16 级灰阶步进 (从 0% 黑 到 100% 白)
        val botY = (topH + midH).toFloat()
        val steps = 16
        val stepW = w.toFloat() / steps
        for (i in 0 until steps) {
            val level = (i * 255f / (steps - 1)).toInt()
            paint.color = Color.rgb(level, level, level)
            canvas.drawRect(i * stepW, botY, (i + 1) * stepW, h.toFloat(), paint)
        }

        // 4. 中心 1:1 电视几何比例圆环 (测试是否形变或拉伸)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.argb(160, 255, 255, 255)
        val radius = h * 0.35f
        canvas.drawCircle(w / 2f, h / 2f, radius, paint)

        // 十字十字对准线
        paint.color = Color.argb(100, 255, 255, 255)
        paint.strokeWidth = 2f
        canvas.drawLine(w / 2f - radius - 40, h / 2f, w / 2f + radius + 40, h / 2f, paint)
        canvas.drawLine(w / 2f, h / 2f - radius - 40, w / 2f, h / 2f + radius + 40, paint)

        // 5. 标尺提示字
        paint.style = Paint.Style.FILL
        paint.textSize = 22f
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("ODIN 3 · SMPTE CALIBRATION SIGNAL · 1080P", w / 2f, 44f, paint)
        canvas.drawText("0% BLACK", stepW * 0.5f, h - 16f, paint)
        canvas.drawText("100% WHITE", w - stepW * 0.5f, h - 16f, paint)

        return bitmap
    }

    /**
     * 生成几何对齐与扫描线测试网格 (Crosshatch Pattern)
     */
    private fun createCrosshatchPattern(w: Int, h: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(14, 16, 22))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(45, 60, 80)
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }

        // 绘制均匀网格 (48px 间距)
        val cellSize = 48f
        var x = 0f
        while (x <= w) {
            paint.color = if ((x.toInt() % (cellSize.toInt() * 4)) == 0) Color.rgb(80, 110, 150) else Color.rgb(35, 48, 65)
            canvas.drawLine(x, 0f, x, h.toFloat(), paint)
            x += cellSize
        }
        var y = 0f
        while (y <= h) {
            paint.color = if ((y.toInt() % (cellSize.toInt() * 4)) == 0) Color.rgb(80, 110, 150) else Color.rgb(35, 48, 65)
            canvas.drawLine(0f, y, w.toFloat(), y, paint)
            y += cellSize
        }

        // 16:9 安全框
        paint.strokeWidth = 3f
        paint.color = Color.rgb(0, 229, 255)
        canvas.drawRect(48f, 48f, w - 48f, h - 48f, paint)

        // 4:3 边框 (居中 1440x1080)
        paint.color = Color.rgb(255, 180, 84)
        val fourByThreeLeft = (w - (h * 4f / 3f)) / 2f
        val fourByThreeRight = w - fourByThreeLeft
        canvas.drawRect(fourByThreeLeft, 48f, fourByThreeRight, h - 48f, paint)

        // 中心圆
        paint.color = Color.WHITE
        paint.strokeWidth = 2.5f
        canvas.drawCircle(w / 2f, h / 2f, h * 0.4f, paint)

        // 标注
        paint.style = Paint.Style.FILL
        paint.textSize = 22f
        paint.color = Color.rgb(255, 180, 84)
        canvas.drawText("4:3 ASPECT", fourByThreeLeft + 20f, 90f, paint)
        paint.color = Color.rgb(0, 229, 255)
        canvas.drawText("16:9 FULLSCREEN", 70f, 90f, paint)

        return bitmap
    }

    /**
     * 生成经典 240p 风格复古像素游戏画面 (用于直观测试 CRT 扫描线、RGB 磷光管、FSR 锐化)
     */
    private fun createRetroPixelPattern(w: Int, h: Int): Bitmap {
        val pw = 320
        val ph = 180
        val pixelBitmap = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(pixelBitmap)
        val paint = Paint()

        // 1. 复古渐变夜空
        for (y in 0 until ph) {
            val ratio = y.toFloat() / ph
            val r = (10 + ratio * 20).toInt()
            val g = (14 + ratio * 35).toInt()
            val b = (35 + ratio * 80).toInt()
            paint.color = Color.rgb(r, g, b)
            canvas.drawRect(0f, y.toFloat(), pw.toFloat(), (y + 1).toFloat(), paint)
        }

        // 2. 远景星空
        paint.color = Color.WHITE
        for (i in 0 until 50) {
            val sx = (Math.sin(i * 123.45) * 0.5 + 0.5) * pw
            val sy = (Math.cos(i * 67.89) * 0.5 + 0.5) * (ph * 0.6)
            canvas.drawPoint(sx.toFloat(), sy.toFloat(), paint)
        }

        // 3. 复古月亮
        val moonX = pw * 0.8f
        val moonY = ph * 0.3f
        paint.color = Color.rgb(255, 235, 160)
        canvas.drawCircle(moonX, moonY, 18f, paint)

        // 4. 远景像素山脉
        paint.color = Color.rgb(28, 42, 68)
        for (x in 0 until pw) {
            val my = 100 + sin(x * 0.05) * 15 + sin(x * 0.02) * 25
            canvas.drawRect(x.toFloat(), my.toFloat(), (x + 1).toFloat(), ph.toFloat(), paint)
        }

        // 5. 近景平台与地面
        paint.color = Color.rgb(38, 98, 52)
        canvas.drawRect(0f, (ph - 36).toFloat(), pw.toFloat(), ph.toFloat(), paint)
        paint.color = Color.rgb(72, 160, 80)
        canvas.drawRect(0f, (ph - 36).toFloat(), pw.toFloat(), (ph - 32).toFloat(), paint)

        // 砖块悬浮平台
        paint.color = Color.rgb(180, 85, 45)
        canvas.drawRect(60f, 90f, 160f, 105f, paint)
        paint.color = Color.rgb(240, 130, 80)
        canvas.drawRect(60f, 90f, 160f, 94f, paint)

        // 6. 经典测试角色小人 (像素精灵)
        paint.color = Color.rgb(255, 60, 60)
        canvas.drawRect(100f, 74f, 114f, 88f, paint)
        paint.color = Color.rgb(255, 200, 150)
        canvas.drawRect(102f, 78f, 112f, 84f, paint)
        paint.color = Color.rgb(40, 80, 220)
        canvas.drawRect(101f, 84f, 113f, 90f, paint)

        // 7. 街机 HUD 文本
        paint.color = Color.rgb(255, 220, 40)
        paint.textSize = 9f
        canvas.drawText("SCORE: 048200", 12f, 18f, paint)
        paint.color = Color.rgb(80, 240, 255)
        canvas.drawText("STAGE 1-1", pw - 68f, 18f, paint)

        return Bitmap.createScaledBitmap(pixelBitmap, w, h, false)
    }
}
