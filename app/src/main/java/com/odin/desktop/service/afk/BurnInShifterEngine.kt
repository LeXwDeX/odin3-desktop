package com.odin.desktop.service.afk

import kotlin.random.Random

/**
 * OLED 防烧屏像素微位移（Pixel Shift）计算引擎。
 * 周期性生成微小不规则坐标偏移，避免固定像素连续发光造成残影或烧屏。
 */
class BurnInShifterEngine(
    private val maxOffsetX: Int = 40,
    private val maxOffsetY: Int = 40
) {
    var currentOffsetX: Int = 0
        private set
    var currentOffsetY: Int = 0
        private set

    fun shift(): Pair<Int, Int> {
        // 生成非零随机位移量，避免回到绝对原点停留过久
        val deltaX = Random.nextInt(-maxOffsetX, maxOffsetX + 1)
        val deltaY = Random.nextInt(-maxOffsetY, maxOffsetY + 1)

        currentOffsetX = deltaX
        currentOffsetY = deltaY
        return Pair(currentOffsetX, currentOffsetY)
    }

    fun reset() {
        currentOffsetX = 0
        currentOffsetY = 0
    }
}
