package io.f7z.olas.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Full-screen image viewer. Invoked as an animated overlay (not a nav destination)
 * so that the zoom-lift enter / exit animations can play above the tab bar.
 *
 * [onDismiss] is called when the user taps the X button, taps the image
 * (when not zoomed in), or drags past the dismiss threshold.
 * The caller is responsible for animating the overlay away.
 */
@Composable
fun FullscreenImageScreen(url: String, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model              = url,
            contentDescription = "Full screen image",
            modifier           = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Mild scale-shrink on drag, matching iOS scaleEffect(max(0.85, 1-drag/600)).
                    val dragScale = (1f - kotlin.math.abs(dragOffsetY) / 600f).coerceIn(0.85f, 1f)
                    scaleX        = scale * dragScale
                    scaleY        = scale * dragScale
                    translationX  = offsetX
                    translationY  = offsetY + dragOffsetY
                    // Match iOS opacity fade: fully transparent at 300 px drag.
                    alpha         = (1f - kotlin.math.abs(dragOffsetY) / 300f).coerceIn(0f, 1f)
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale    = (scale * zoom).coerceIn(0.5f, 5f)
                        offsetX += pan.x * scale
                        offsetY += pan.y * scale
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (kotlin.math.abs(dragOffsetY) > 150f) {
                                // Reset before dismiss so the exit scale animation
                                // starts from center rather than the dragged position.
                                dragOffsetY = 0f
                                onDismiss()
                            } else {
                                dragOffsetY = 0f
                            }
                        },
                        onDragCancel = { dragOffsetY = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            if (scale <= 1.05f) dragOffsetY += dragAmount
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        // Single tap dismisses (when not zoomed in), matching iOS.
                        onTap = { if (scale <= 1.05f) onDismiss() },
                        onDoubleTap = {
                            scale = if (scale > 1.5f) 1f else 2.5f
                            offsetX = 0f; offsetY = 0f
                        },
                    )
                },
        )

        // X close button — top-trailing corner, mirrors iOS.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 56.dp, end = 12.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector        = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint               = Color.White,
                    modifier           = Modifier.size(18.dp),
                )
            }
        }
    }
}
