package com.odin.desktop.ui.components

import android.widget.ImageView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.odin.desktop.data.model.InstalledApp
import com.odin.desktop.ui.theme.CardBackground
import com.odin.desktop.ui.theme.CardBorder
import com.odin.desktop.ui.theme.CyanAccent

@Composable
fun AppCard(
    app: InstalledApp,
    isFocused: Boolean,
    isReordering: Boolean = false,
    isPicked: Boolean = false,
    cardIndex: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetScale = when {
        isPicked -> 1.18f
        isFocused -> 1.10f
        isReordering -> 1.02f
        else -> 1.0f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        label = "card_scale"
    )

    // 类似 iOS / macOS 的自然波浪微抖动 (Jiggle / Wobble)
    val infiniteTransition = rememberInfiniteTransition(label = "jiggle_$cardIndex")
    val jiggleRotation by if (isReordering && !isPicked) {
        val duration = 120 + (cardIndex % 3) * 25
        infiniteTransition.animateFloat(
            initialValue = -2.2f,
            targetValue = 2.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(duration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "jiggle_rot_$cardIndex"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val jiggleTranslationY by if (isReordering && !isPicked) {
        val duration = 140 + ((cardIndex + 1) % 3) * 25
        infiniteTransition.animateFloat(
            initialValue = -1.2f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(duration, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "jiggle_trans_$cardIndex"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    // 固定外层槽位尺寸 128.dp，保证卡片放大或抖动时绝对不挤压或推移上下左右邻近元素
    Box(
        modifier = modifier.size(128.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    rotationZ = if (isPicked) 0f else jiggleRotation
                    translationY = if (isPicked) -8f else jiggleTranslationY
                }
                .scale(scale)
                .size(110.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isPicked) CyanAccent.copy(alpha = 0.15f) else CardBackground)
                .border(
                    width = when {
                        isPicked -> 3.5.dp
                        isFocused -> 2.5.dp
                        isReordering -> 1.5.dp
                        else -> 1.dp
                    },
                    color = when {
                        isPicked -> CyanAccent
                        isFocused -> CyanAccent
                        isReordering -> CardBorder.copy(alpha = 0.8f)
                        else -> CardBorder
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable { onClick() }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                },
                update = { imageView ->
                    imageView.setImageDrawable(app.icon)
                },
                modifier = Modifier.size(72.dp)
            )
        }
    }
}
