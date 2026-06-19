package io.f7z.olas.feature.compose

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // NMP-GAP(#23): Picker constraints (max selection, ingestion policy) must come from Rust config.
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10),
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris = uris
            onSelected(uris)
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
        )
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
            text     = "Tap to select up to 10.",
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
