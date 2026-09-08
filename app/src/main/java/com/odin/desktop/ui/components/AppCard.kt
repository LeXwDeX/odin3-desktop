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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.viewinterop.AndroidView
import com.odin.desktop.data.model.InstalledApp
import com.odin.desktop.ui.components.base.ImageTileSize
import com.odin.desktop.ui.components.base.OdinImageTile
import com.odin.desktop.ui.theme.LocalOdinPalette

@Composable
fun AppCard(
    app: InstalledApp,
    isFocused: Boolean,
    isReordering: Boolean = false,
    isPicked: Boolean = false,
    cardIndex: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
    compact: Boolean = false,
    interactive: Boolean = true,
    onLongClick: () -> Unit = {}
) {
    val palette = LocalOdinPalette.current
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

    OdinImageTile(label = app.label, onClick = onClick, modifier = modifier,
        size = if (compact) ImageTileSize.DENSE else ImageTileSize.STANDARD,
        focused = isFocused || isPicked, selected = isPicked, hidden = hidden,
        interactive = interactive, onLongClick = onLongClick,
        transform = Modifier.graphicsLayer {
            rotationZ = if (isPicked) 0f else jiggleRotation
            translationY = if (isPicked) -8f else jiggleTranslationY
        }.scale(scale)) {
        AndroidView(factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
            update = { it.setImageDrawable(app.icon) }, modifier = Modifier.fillMaxSize())
    }
}
