package io.f7z.olas.feature.onboarding

// P0-A: hardcoded STARTER_PACKS removed; packs are discovered from real
// kind:30000 events via the kernel event observer and decoded by Rust.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.f7z.olas.core.FollowPackDescriptor
import io.f7z.olas.ui.theme.OlasColors

@Composable
fun FollowPacksScreen(
    onContinue: () -> Unit,
    vm: OnboardingViewModel = viewModel(),
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    // Start/stop discovery with the screen's lifecycle.
    LaunchedEffect(Unit) { vm.startPackDiscovery() }
    DisposableEffect(Unit) { onDispose { vm.stopPackDiscovery() } }

    val selectedCount  = uiState.selectedPackIds.size
    val totalCreators  = uiState.discoveredPacks
        .filter { it.id in uiState.selectedPackIds }
        .sumOf { it.count }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProgressDots(currentStep = 1, totalSteps = 2)
            Spacer(Modifier.height(24.dp))
            Text(
                "Follow some people",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = OlasColors.Text1,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Pick a few packs to see great photos right away.",
                fontSize = 15.sp,
                color = OlasColors.Text2,
            )
        }

        if (uiState.discoveredPacks.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = OlasColors.Text1)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.discoveredPacks, key = { it.id }) { pack ->
                    FollowPackCard(
                        pack     = pack,
                        enabled  = pack.id in uiState.selectedPackIds,
                        onToggle = { vm.togglePackSelection(pack.id) },
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(OlasColors.Background)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            if (selectedCount > 0) {
                Text(
                    text     = "$selectedCount pack${if (selectedCount == 1) "" else "s"} · $totalCreators creators",
                    color    = OlasColors.Text2,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick  = { vm.applySelectedPacks(); onContinue() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = OlasColors.Text1,
                    contentColor   = OlasColors.Background,
                ),
            ) {
                Text(
                    if (selectedCount > 0) "Continue" else "Skip",
                    color      = OlasColors.Background,
                    fontSize   = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun FollowPackCard(
    pack: FollowPackDescriptor,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OlasColors.Surface)
            .border(
                width = if (enabled) 1.dp else 0.dp,
                color = if (enabled) OlasColors.Text1 else OlasColors.Border,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(pack.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OlasColors.Text1)
            Spacer(Modifier.height(2.dp))
            Text(pack.description, fontSize = 13.sp, color = OlasColors.Text2)
            Spacer(Modifier.height(4.dp))
            // Preview avatars using real member pubkeys (first 6)
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                pack.pubkeys.take(6).forEach { pubkey ->
                    // Deterministic color avatar from pubkey prefix
                    val color = pubkeyToColor(pubkey)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text("${pack.count} creators", fontSize = 12.sp, color = OlasColors.Text3)
        }
        Switch(
            checked         = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor       = OlasColors.Background,
                checkedTrackColor       = OlasColors.Text1,
                uncheckedThumbColor     = OlasColors.Text3,
                uncheckedTrackColor     = OlasColors.Surface2,
            ),
        )
    }
}

/** Deterministic avatar fill color from the first 3 bytes of the pubkey hex. */
private fun pubkeyToColor(pubkey: String): Color {
    if (pubkey.length < 6) return Color(0xFF8B5CF6)
    return try {
        val r = pubkey.substring(0, 2).toInt(16)
        val g = pubkey.substring(2, 4).toInt(16)
        val b = pubkey.substring(4, 6).toInt(16)
        Color(r, g, b, 0xFF)
    } catch (_: Exception) {
        Color(0xFF8B5CF6)
    }
}
