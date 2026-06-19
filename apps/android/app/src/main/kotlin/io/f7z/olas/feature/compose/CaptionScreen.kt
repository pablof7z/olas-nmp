package io.f7z.olas.feature.compose

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.ui.theme.OlasColors

@Composable
fun CaptionScreen(
    uris: List<Uri>,
    onShare: () -> Unit,
) {
    val vm: UploadViewModel = viewModel()
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var caption by remember { mutableStateOf("") }
    // Blossom server URL comes from Rust-owned server-config projection.
    val blossomUrl = remember { NMPBridge.blossomServerUrl() }
    // Geohash is computed by Rust when location is toggled on (NMPBridge.computeGeohash(lat, lon, 6)).
    var locationEnabled by remember { mutableStateOf(false) }
    val altTexts = remember { mutableMapOf<Uri, String>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        // Hashtag-colored caption field
        val annotated = buildAnnotatedString {
            val words = caption.split(" ")
            words.forEachIndexed { i, word ->
                if (word.startsWith("#")) {
                    withStyle(SpanStyle(color = OlasColors.Blue, fontWeight = FontWeight.SemiBold)) {
                        append(word)
                    }
                } else {
                    append(word)
                }
                if (i < words.size - 1) append(" ")
            }
        }

        TextField(
            value         = caption,
            onValueChange = { caption = it },
            modifier      = Modifier.fillMaxWidth(),
            placeholder   = { Text("Write a caption...", color = OlasColors.Text3) },
            colors        = TextFieldDefaults.colors(
                focusedContainerColor   = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor        = OlasColors.Text1,
                unfocusedTextColor      = OlasColors.Text1,
                focusedIndicatorColor   = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            minLines = 3,
        )

        HorizontalDivider(color = OlasColors.Border)

        // Alt text chips per image
        uris.forEachIndexed { index, uri ->
            Row(
                modifier          = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = "Photo ${index + 1} alt text",
                    color    = OlasColors.Text2,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        HorizontalDivider(color = OlasColors.Border)

        // Location toggle
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Location", color = OlasColors.Text1, fontSize = 16.sp)
                if (locationEnabled) {
                    Text("Privacy note: your location will be public.", color = OlasColors.Text2, fontSize = 12.sp)
                }
            }
            Switch(
                checked         = locationEnabled,
                onCheckedChange = { locationEnabled = it },
                colors          = SwitchDefaults.colors(
                    checkedThumbColor   = OlasColors.Background,
                    checkedTrackColor   = OlasColors.Text1,
                    uncheckedTrackColor = OlasColors.Surface2,
                ),
            )
        }

        Spacer(Modifier.weight(1f))

        if (state.error != null) {
            Text(state.error!!, color = OlasColors.Destructive, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick  = {
                vm.upload(context, uris, caption, altTexts)
                onShare()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape    = RoundedCornerShape(12.dp),
            enabled  = state.step == UploadStep.IDLE || state.step == UploadStep.ERROR,
            colors   = ButtonDefaults.buttonColors(
                containerColor = OlasColors.Text1,
                contentColor   = OlasColors.Background,
            ),
        ) {
            if (state.step != UploadStep.IDLE && state.step != UploadStep.ERROR && state.step != UploadStep.DONE) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = OlasColors.Background)
            } else {
                Text("Share", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
