package io.f7z.olas.feature.compose

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.f7z.olas.ui.theme.OlasColors

@Composable
fun EditPhotoScreen(uri: Uri, onNext: (selectedFilter: PhotoFilter, intensity: Float) -> Unit) {
    var selectedFilter by remember { mutableStateOf(FILTERS.first()) }
    var intensity by remember { mutableStateOf(0.75f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background),
    ) {
        // Full-width image preview
        val matrix = if (selectedFilter.name == "Original") identityMatrix()
        else blendMatrix(identityMatrix(), selectedFilter.matrix, intensity)

        AsyncImage(
            model              = uri,
            contentDescription = "Edit preview",
            modifier           = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f),
            contentScale       = ContentScale.Crop,
            colorFilter        = ColorFilter.colorMatrix(ColorMatrix(matrix)),
        )

        Spacer(Modifier.height(16.dp))

        FilterCarousel(
            imageUri           = uri,
            selectedFilter     = selectedFilter.name,
            intensity          = intensity,
            onFilterSelected   = { selectedFilter = it },
            onIntensityChanged = { intensity = it },
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick  = { onNext(selectedFilter, intensity) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 24.dp),
            shape  = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OlasColors.Text1,
                contentColor   = OlasColors.Background,
            ),
        ) {
            Text(
                "Next",
                color = OlasColors.Background,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

// Expose helpers from FilterCarousel.kt for use here via file-level references
private fun identityMatrix() = io.f7z.olas.feature.compose.FILTERS.first().matrix
private fun blendMatrix(base: FloatArray, overlay: FloatArray, t: Float) =
    FloatArray(20) { i -> base[i] * (1f - t) + overlay[i] * t }
