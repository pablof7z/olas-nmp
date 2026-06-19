package io.f7z.olas.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage
import androidx.navigation.NavController

@Composable
fun FullscreenImageScreen(navController: NavController, url: String) {
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
                    scaleX        = scale
                    scaleY        = scale
                    translationX  = offsetX
                    translationY  = offsetY + dragOffsetY
                    alpha         = (1f - (kotlin.math.abs(dragOffsetY) / 600f)).coerceIn(0f, 1f)
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
                                navController.popBackStack()
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
                    detectTapGestures(onDoubleTap = {
                        scale = if (scale > 1.5f) 1f else 2.5f
                        offsetX = 0f; offsetY = 0f
                    })
                },
        )
    }
}
