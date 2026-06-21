package io.f7z.olas.feature.compose

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.core.OlasHaptics
import io.f7z.olas.core.OlasProfileHost
import io.f7z.olas.core.OlasSound
import io.f7z.olas.ui.theme.OlasColors
import org.nmp.registry.NostrAvatar
import org.nmp.registry.ProfileWire

@Composable
fun CaptionScreen(
    uris: List<Uri>,
    filter: PhotoFilter,
    intensity: Float,
    onShare: () -> Unit,
) {
    val vm: UploadViewModel = viewModel()
    val context = LocalContext.current
    val view = LocalView.current
    val state by vm.state.collectAsStateWithLifecycle()
    var caption by remember { mutableStateOf("") }
    // Delay before Share is interactive to prevent accidental trigger from the
    // filter→caption transition where "Next" and "Share" occupy the same position.
    var shareReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(400L); shareReady = true }
    // Blossom server URL comes from Rust-owned server-config projection.
    val blossomUrl = remember { NMPBridge.blossomServerUrl() }
    // Geohash is computed by Rust when location is toggled on (NMPBridge.computeGeohash(lat, lon, 6)).
    var locationEnabled by remember { mutableStateOf(false) }
    val altTexts = remember { mutableMapOf<Uri, String>() }

    // P3-C: @mention autocomplete — mirrors iOS CaptionView.mentionDropdown
    val allProfiles by OlasProfileHost.profilesFlow.collectAsStateWithLifecycle()
    var mentionQuery by remember { mutableStateOf<String?>(null) }
    val mentionResults by remember {
        derivedStateOf {
            val q = mentionQuery ?: return@derivedStateOf emptyList<ProfileWire>()
            allProfiles.values
                .filter { p -> (p.displayName ?: "").lowercase().contains(q) }
                .sortedWith(compareByDescending<ProfileWire> {
                    (it.displayName ?: "").lowercase().startsWith(q)
                }.thenBy { it.displayName ?: "" })
                .take(5)
        }
    }

    // Navigate away only after upload completes — keeps ViewModel alive until then.
    LaunchedEffect(state.step) {
        if (state.step == UploadStep.DONE) {
            OlasHaptics.notificationSuccess(view)
            OlasSound.shutterSoft(context)
            onShare()
        }
    }

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

        Box {
            TextField(
                value         = caption,
                onValueChange = { newValue ->
                    caption = newValue
                    // Update mention query: last token starting with '@'
                    val tokens = newValue.split(" ", "\n")
                    val last = tokens.lastOrNull() ?: ""
                    mentionQuery = if (last.startsWith("@") && last.length > 1) {
                        last.drop(1).lowercase()
                    } else null
                },
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

            // @mention autocomplete dropdown — mirrors iOS CaptionView.mentionDropdown
            if (mentionQuery != null && mentionResults.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .shadow(8.dp, RoundedCornerShape(10.dp))
                        .background(OlasColors.Surface2, RoundedCornerShape(10.dp)),
                ) {
                    mentionResults.forEachIndexed { index, profile ->
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Replace trailing '@query' with 'nostr:<npub>'
                                    if (profile.npub.isNotEmpty()) {
                                        val tokens = caption.split(" ").toMutableList()
                                        if (tokens.lastOrNull()?.startsWith("@") == true) {
                                            tokens.removeLast()
                                        }
                                        tokens.add("nostr:${profile.npub}")
                                        caption = tokens.joinToString(" ") + " "
                                        mentionQuery = null
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NostrAvatar(profile = profile, size = 28.dp)
                            Spacer(Modifier.size(8.dp))
                            Column {
                                Text(
                                    text      = profile.displayName ?: profile.npubShort,
                                    color     = OlasColors.Text1,
                                    fontSize  = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (!profile.nip05.isNullOrEmpty()) {
                                    Text(
                                        text     = profile.nip05,
                                        color    = OlasColors.Text3,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        if (index < mentionResults.size - 1) {
                            HorizontalDivider(color = OlasColors.Border)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = OlasColors.Border)

        // P0-B: alt text field per image (collected and passed to Rust imeta "alt").
        uris.forEachIndexed { index, uri ->
            Row(
                modifier          = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = "Photo ${index + 1}:",
                    color    = OlasColors.Text2,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(end = 8.dp),
                )
                TextField(
                    value         = altTexts[uri] ?: "",
                    onValueChange = { altTexts[uri] = it },
                    modifier      = Modifier.weight(1f),
                    placeholder   = { Text("Alt text (accessibility)", color = OlasColors.Text3, fontSize = 13.sp) },
                    singleLine    = true,
                    colors        = TextFieldDefaults.colors(
                        focusedContainerColor   = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor        = OlasColors.Text1,
                        unfocusedTextColor      = OlasColors.Text1,
                        focusedIndicatorColor   = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
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
                val geohash = if (locationEnabled) currentCoarseGeohash4(context) else null
                vm.upload(context, uris, caption, altTexts, geohash, filter, intensity)
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape    = RoundedCornerShape(12.dp),
            enabled  = shareReady && (state.step == UploadStep.IDLE || state.step == UploadStep.ERROR),
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
