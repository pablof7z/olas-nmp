package io.f7z.olas.feature.compose

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import io.f7z.olas.BuildConfig
import java.io.File
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import io.f7z.olas.core.NMPBridge
import org.json.JSONObject
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.f7z.olas.ui.theme.OlasColors

@Composable
fun PhotoPickerScreen(onSelected: (List<Uri>) -> Unit) {
    val context = LocalContext.current
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // Load picker constraints from Rust (max_selection, etc.).
    val maxSelection = remember {
        val configJson = NMPBridge.pickerConfigJson() ?: """{"max_selection":10}"""
        runCatching { JSONObject(configJson).optInt("max_selection", 10) }.getOrElse { 10 }
    }

    // PickMultipleVisualMedia requires maxItems >= 2; use single-select for maxSelection <= 1.
    val singleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let {
            selectedUris = listOf(it)
            onSelected(listOf(it))
        }
    }
    val multiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxSelection.coerceAtLeast(2)),
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris = uris
            onSelected(uris)
        }
    }

    LaunchedEffect(Unit) {
        // CI bypass: if debug_compose_photo.jpg exists in the app's external files dir, skip the picker.
        if (BuildConfig.DEBUG) {
            val debugFile = File(context.getExternalFilesDir(null), "debug_compose_photo.jpg")
            if (debugFile.exists()) {
                val uri = Uri.fromFile(debugFile)
                onSelected(listOf(uri))
                return@LaunchedEffect
            }
        }
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (maxSelection <= 1) singleLauncher.launch(request) else multiLauncher.launch(request)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background)
            .padding(16.dp),
    ) {
        Text(
            text       = "Select photos",
            fontSize   = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color      = OlasColors.Text1,
        )
        Text(
            text     = if (maxSelection <= 1) "Tap to select a photo." else "Tap to select up to $maxSelection.",
            fontSize = 13.sp,
            color    = OlasColors.Text2,
        )

        if (selectedUris.isNotEmpty()) {
            LazyVerticalGrid(
                columns             = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalArrangement   = Arrangement.spacedBy(1.dp),
                modifier            = Modifier.fillMaxWidth(),
            ) {
                items(selectedUris) { uri ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(0.dp))
                            .background(OlasColors.Surface)
                            .border(1.dp, OlasColors.Border),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model              = uri,
                            contentDescription = "Selected photo",
                            modifier           = Modifier.fillMaxSize(),
                            contentScale       = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}
