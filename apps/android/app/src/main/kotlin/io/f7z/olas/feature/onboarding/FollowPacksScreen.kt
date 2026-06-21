package io.f7z.olas.feature.onboarding

// Follow packs are sourced entirely from the Rust onboarding snapshot
// (`olas_follow_packs_snapshot_json`). Compose only renders the wire struct and
// forwards opaque pack ids back; it never inspects protocol details.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import io.f7z.olas.core.FollowPack
import io.f7z.olas.core.FollowPackAvatar
import io.f7z.olas.ui.theme.OlasColors

@Composable
fun FollowPacksScreen(
    onContinue: () -> Unit,
    vm: OnboardingViewModel = viewModel(),
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.startPackDiscovery() }
    DisposableEffect(Unit) { onDispose { vm.stopPackDiscovery() } }

    val snapshot      = uiState.followPacks
    val selectedIds   = uiState.selectedPackIds
    val selectedPacks = (snapshot?.packs ?: emptyList()).filter { it.id in selectedIds }
    val packCount     = selectedPacks.size
    val peopleCount   = selectedPacks.sumOf { it.memberCount }

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
            Text("Follow some people", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = OlasColors.Text1)
            Spacer(Modifier.height(8.dp))
            Text("Get a better feed right away", fontSize = 15.sp, color = OlasColors.Text2)
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (snapshot?.state) {
                "empty_offline" -> OfflineView(onRetry = { vm.startPackDiscovery() })
                "ready"         -> PackList(
                    packs       = snapshot.packs,
                    selectedIds = selectedIds,
                    onToggle    = { vm.togglePackSelection(it) },
                )
                else            -> SkeletonView()
            }
        }

        Footer(
            hasSelection = selectedIds.isNotEmpty(),
            packCount    = packCount,
            peopleCount  = peopleCount,
            onPrimary    = { vm.applySelectedPacks(); onContinue() },
        )
    }
}

@Composable
private fun PackList(
    packs: List<FollowPack>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
) {
    // One continuous mixed list — no section dividers.
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(packs, key = { it.id }) { pack ->
            FollowPackCard(
                pack       = pack,
                isSelected = pack.id in selectedIds,
                onToggle   = { onToggle(pack.id) },
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SkeletonView() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) {
            Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(14.dp)).background(OlasColors.Surface))
        }
    }
}

@Composable
private fun OfflineView(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.WifiOff, contentDescription = null, tint = OlasColors.Text3, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "We couldn't load suggestions right now",
            fontSize = 16.sp,
            color = OlasColors.Text2,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) {
            Text("Try again", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OlasColors.Blue)
        }
    }
}

@Composable
private fun Footer(
    hasSelection: Boolean,
    packCount: Int,
    peopleCount: Int,
    onPrimary: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OlasColors.Background)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (hasSelection) {
            val packsLabel  = if (packCount == 1) "pack" else "packs"
            val peopleLabel = if (peopleCount == 1) "person" else "people"
            Text(
                text     = "$packCount $packsLabel · $peopleCount $peopleLabel",
                color    = OlasColors.Text2,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick  = onPrimary,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = OlasColors.Text1,
                contentColor   = OlasColors.Background,
            ),
        ) {
            Text(
                if (hasSelection) "Continue" else "Skip",
                color      = OlasColors.Background,
                fontSize   = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FollowPackCard(
    pack: FollowPack,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(OlasColors.Surface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) OlasColors.Text1 else OlasColors.Border,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable { onToggle() },
    ) {
        // Cover image with optional "Featured" chip.
        Box(modifier = Modifier.fillMaxWidth().height(110.dp).background(OlasColors.Surface2)) {
            if (pack.coverImageUrl != null) {
                AsyncImage(
                    model              = pack.coverImageUrl,
                    contentDescription = null,
                    modifier           = Modifier.fillMaxSize(),
                    contentScale       = ContentScale.Crop,
                )
            }
            if (pack.featured) {
                Text(
                    "Featured",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = OlasColors.Text1,
                    modifier   = Modifier
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(pack.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OlasColors.Text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val description = pack.description
                if (!description.isNullOrEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(description, fontSize = 13.sp, color = OlasColors.Text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    pack.previewAvatars.take(6).forEachIndexed { index, avatar ->
                        PackAvatar(avatar = avatar, modifier = Modifier.offset(x = (index * -10).dp))
                    }
                    Spacer(Modifier.weight(1f))
                    val peopleLabel = if (pack.memberCount == 1) "person" else "people"
                    Text("${pack.memberCount} $peopleLabel", fontSize = 12.sp, color = OlasColors.Text2)
                }
            }
            Spacer(Modifier.size(8.dp))
            Icon(
                imageVector        = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint               = if (isSelected) OlasColors.Text1 else OlasColors.Border,
                modifier           = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun PackAvatar(avatar: FollowPackAvatar, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(28.dp).clip(CircleShape).background(OlasColors.Surface2),
        contentAlignment = Alignment.Center,
    ) {
        if (avatar.imageUrl != null) {
            AsyncImage(
                model = avatar.imageUrl, contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                avatar.displayName?.firstOrNull()?.uppercase() ?: "",
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = OlasColors.Text2,
            )
        }
    }
}
