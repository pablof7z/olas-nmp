package io.f7z.olas.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Sweeps a highlight band left → right over the composable it is applied to.
 *
 * Pass [reduceMotion] = true (from [LocalAccessibilityManager] or a settings flag)
 * to suppress the animation — the receiver is rendered as-is with no overlay.
 */
fun Modifier.shimmer(reduceMotion: Boolean = false): Modifier = composed {
    if (reduceMotion) return@composed this

    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerPhase",
    )

    val highlight = Color.White.copy(alpha = 0.12f)

    drawWithContent {
        drawContent()
        val w = size.width
        // Sweep: gradient is 60% of width, centered at phase * w
        val sweepW = w * 0.6f
        val startX = phase * w - sweepW / 2f
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(startX, 0f),
                end = Offset(startX + sweepW, size.height),
            ),
        )
    }
}
