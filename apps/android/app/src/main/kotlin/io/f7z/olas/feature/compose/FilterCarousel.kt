package io.f7z.olas.feature.compose

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.core.OlasHaptics
import io.f7z.olas.ui.theme.OlasColors
import org.json.JSONArray

data class PhotoFilter(val name: String, val matrix: FloatArray)

/** Local registry mapping filter IDs (from Rust catalog) to CIEffect-equivalent matrices. */
private val FILTER_REGISTRY: Map<String, PhotoFilter> = mapOf(
    "original" to PhotoFilter("Original", identityMatrix()),
    "daylight" to PhotoFilter("Daylight", warmMatrix(0.15f)),
    "ember"    to PhotoFilter("Ember",    warmMatrix(0.30f)),
    "dusk"     to PhotoFilter("Dusk",     coolMatrix(0.2f)),
    "mist"     to PhotoFilter("Mist",     fadeMatrix(0.15f)),
    "chrome"   to PhotoFilter("Chrome",   contrastMatrix(1.4f)),
    "film"     to PhotoFilter("Film",     filmMatrix()),
    "fade"     to PhotoFilter("Fade",     fadeMatrix(0.25f)),
    "grain"    to PhotoFilter("Grain",    desaturateMatrix(0.3f)),
    "arctic"   to PhotoFilter("Arctic",   coolMatrix(0.35f)),
    "copper"   to PhotoFilter("Copper",   copperMatrix()),
    "veil"     to PhotoFilter("Veil",     veilMatrix()),
    "bloom"    to PhotoFilter("Bloom",    bloomMatrix()),
)

/** Load the ordered filter catalog from Rust; fall back to the full local registry if unavailable. */
fun loadFilterCatalog(): List<PhotoFilter> {
    val catalogJson = NMPBridge.filterCatalogJson() ?: return FILTER_REGISTRY.values.toList()
    return runCatching {
        val arr = JSONArray(catalogJson)
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optString("id").lowercase()
            FILTER_REGISTRY[id]
        }.ifEmpty { FILTER_REGISTRY.values.toList() }
    }.getOrElse { FILTER_REGISTRY.values.toList() }
}

val FILTERS: List<PhotoFilter> get() = loadFilterCatalog()

@Composable
fun FilterCarousel(
    imageUri: Uri,
    selectedFilter: String,
    intensity: Float,
    onFilterSelected: (PhotoFilter) -> Unit,
    onIntensityChanged: (Float) -> Unit,
) {
    Column {
        LazyRow(
            contentPadding      = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(FILTERS) { filter ->
                FilterThumbnail(
                    uri        = imageUri,
                    filter     = filter,
                    isSelected = filter.name == selectedFilter,
                    intensity  = if (filter.name == selectedFilter) intensity else 1f,
                    onClick    = { onFilterSelected(filter) },
                )
            }
        }
        if (selectedFilter != "Original") {
            Spacer(Modifier.height(8.dp))
            Text(
                text     = "Intensity",
                fontSize = 13.sp,
                color    = OlasColors.Text2,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Slider(
                value         = intensity,
                onValueChange = onIntensityChanged,
                valueRange    = 0f..1f,
                modifier      = Modifier.fillMaxWidth().height(32.dp),
                colors        = SliderDefaults.colors(
                    thumbColor       = OlasColors.Text1,
                    activeTrackColor = OlasColors.Text1,
                ),
            )
        }
    }
}

@Composable
private fun FilterThumbnail(
    uri: Uri,
    filter: PhotoFilter,
    isSelected: Boolean,
    intensity: Float,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.clickable {
            OlasHaptics.impactRigid(view)
            onClick()
        },
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = OlasColors.Text1,
                    shape = RoundedCornerShape(8.dp),
                ),
        ) {
            val matrix = if (filter.name == "Original") identityMatrix()
            else blendMatrix(identityMatrix(), filter.matrix, intensity)
            AsyncImage(
                model              = uri,
                contentDescription = filter.name,
                modifier           = Modifier.fillMaxWidth().aspectRatio(1f),
                contentScale       = ContentScale.Crop,
                colorFilter        = ColorFilter.colorMatrix(ColorMatrix(matrix)),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text       = filter.name,
            fontSize   = 10.sp,
            color      = if (isSelected) OlasColors.Text1 else OlasColors.Text2,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ---- Filter matrix helpers ----

internal fun identityMatrix() = floatArrayOf(
    1f,0f,0f,0f,0f,
    0f,1f,0f,0f,0f,
    0f,0f,1f,0f,0f,
    0f,0f,0f,1f,0f,
)

private fun warmMatrix(t: Float) = floatArrayOf(
    1f+t,0f,0f,0f,0f,
    0f,1f,0f,0f,0f,
    0f,0f,1f-t*0.5f,0f,0f,
    0f,0f,0f,1f,0f,
)

private fun coolMatrix(t: Float) = floatArrayOf(
    1f-t,0f,0f,0f,0f,
    0f,1f,0f,0f,0f,
    0f,0f,1f+t*0.5f,0f,0f,
    0f,0f,0f,1f,0f,
)

private fun fadeMatrix(t: Float) = floatArrayOf(
    1f-t,0f,0f,0f,t*255f*0.3f,
    0f,1f-t,0f,0f,t*255f*0.3f,
    0f,0f,1f-t,0f,t*255f*0.3f,
    0f,0f,0f,1f,0f,
)

private fun contrastMatrix(c: Float): FloatArray {
    val t = (1f - c) / 2f * 255f
    return floatArrayOf(
        c,0f,0f,0f,t,
        0f,c,0f,0f,t,
        0f,0f,c,0f,t,
        0f,0f,0f,1f,0f,
    )
}

private fun desaturateMatrix(amount: Float): FloatArray {
    val sr = 0.2126f * amount; val sg = 0.7152f * amount; val sb = 0.0722f * amount
    val ir = 1f - sr; val ig = 1f - sg; val ib = 1f - sb
    return floatArrayOf(
        ir,sg,sb,0f,0f,
        sr,ig,sb,0f,0f,
        sr,sg,ib,0f,0f,
        0f,0f,0f,1f,0f,
    )
}

private fun filmMatrix()   = blendMatrix(contrastMatrix(1.1f), desaturateMatrix(0.15f), 1f)
private fun copperMatrix() = warmMatrix(0.2f)
private fun veilMatrix()   = blendMatrix(fadeMatrix(0.1f), coolMatrix(0.1f), 1f)
private fun bloomMatrix()  = blendMatrix(warmMatrix(0.1f), contrastMatrix(1.15f), 1f)

internal fun blendMatrix(base: FloatArray, overlay: FloatArray, t: Float): FloatArray {
    return FloatArray(20) { i -> base[i] * (1f - t) + overlay[i] * t }
}
