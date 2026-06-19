package io.f7z.olas.feature.compose

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import io.f7z.olas.ui.theme.OlasColors
import kotlinx.coroutines.launch

// NMP-GAP(#22): Compose step routing and transitions must be driven by a Rust state machine, not Kotlin navigation state.
private sealed interface ComposeStep {
    object Pick : ComposeStep
    data class Edit(val uris: List<Uri>) : ComposeStep
    data class Caption(val uris: List<Uri>, val filter: PhotoFilter, val intensity: Float) : ComposeStep
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(navController: NavController) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<ComposeStep>(ComposeStep.Pick) }

    ModalBottomSheet(
        onDismissRequest   = { navController.popBackStack() },
        sheetState         = sheetState,
        containerColor     = OlasColors.Background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(OlasColors.Background),
        ) {
            when (val current = step) {
                is ComposeStep.Pick -> {
                    PhotoPickerScreen(onSelected = { uris ->
                        if (uris.isNotEmpty()) step = ComposeStep.Edit(uris)
                    })
                }
                is ComposeStep.Edit -> {
                    EditPhotoScreen(
                        uri    = current.uris.first(),
                        onNext = { filter, intensity ->
                            step = ComposeStep.Caption(current.uris, filter, intensity)
                        },
                    )
                }
                is ComposeStep.Caption -> {
                    CaptionScreen(
                        uris    = current.uris,
                        onShare = {
                            scope.launch {
                                sheetState.hide()
                                navController.popBackStack()
                            }
                        },
                    )
                }
            }
        }
    }
}
