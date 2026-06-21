package io.f7z.olas.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.nmp.registry.LocalNostrProfileHost
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.core.discoverSectionsJson
import io.f7z.olas.ui.theme.OlasColors
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ── Discover data models ─────────────────────────────────────────────────────

@Serializable
data class DiscoverProfile(val pubkey: String, val mutual_count: Int)

@Serializable
data class DiscoverSection(val title: String, val reason: String, val profiles: List<DiscoverProfile>)

// ── DiscoverScreen composable ─────────────────────────────────────────────────

@Composable
fun DiscoverScreen() {
    val activePubkey by NMPBridge.activeAccountPubkeyFlow.collectAsStateWithLifecycle()
    var sections by remember { mutableStateOf<List<DiscoverSection>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val jsonParser = remember { Json { ignoreUnknownKeys = true } }

    LaunchedEffect(activePubkey) {
        val pk = activePubkey ?: return@LaunchedEffect
        isLoading = true
        val raw = NMPBridge.discoverSectionsJson(pk)
        sections = if (raw != null) {
            runCatching { jsonParser.decodeFromString<List<DiscoverSection>>(raw) }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }
        isLoading = false
    }

    val hasRealSections = sections.any { it.reason != "graph_empty" && it.profiles.isNotEmpty() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))

        when {
            isLoading -> {
                SuggestedPlaceholderSection(title = "Suggested for you")
            }
            !hasRealSections -> {
                // Honest fallback: WoT graph not bootstrapped or no follows yet.
                Column(
                    modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text     = "Follow some accounts to see suggestions here.",
                        fontSize = 14.sp,
                        color    = OlasColors.Text2,
                    )
                }
            }
            else -> {
                sections.filter { it.profiles.isNotEmpty() && it.reason != "graph_empty" }
                    .forEach { section ->
                        SuggestedSection(section = section)
                        Spacer(Modifier.height(24.dp))
                    }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── Section rendering ─────────────────────────────────────────────────────────

@Composable
private fun SuggestedSection(section: DiscoverSection) {
    val profileHost = LocalNostrProfileHost.current
    Column {
        Text(
            text       = section.title,
            fontSize   = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color      = OlasColors.Text1,
            modifier   = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding        = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(section.profiles.take(15), key = { it.pubkey }) { dp ->
                // Claim the profile so the kernel fetches kind:0; release when the
                // item leaves the composition (LazyRow scrolls it out of view).
                DisposableEffect(dp.pubkey) {
                    profileHost?.claimProfile(dp.pubkey, "olas.discover")
                    onDispose { profileHost?.releaseProfile(dp.pubkey, "olas.discover") }
                }
                // Read the resolved profile reactively — recomposes when kind:0 arrives.
                val wire = profileHost?.profileForPubkey(dp.pubkey)
                val mutualFollowName = if (dp.mutual_count > 0) "${dp.mutual_count} of your follows" else null
                SuggestedAccountCard(
                    account = SuggestedAccount(
                        pubkey            = dp.pubkey,
                        // wire?.display falls back to npubShort (Rust-formatted); show "…"
                        // only before the kernel has delivered *any* profile data.
                        name              = wire?.display ?: "…",
                        avatarUrl         = wire?.avatarUrl,
                        photoUrls         = emptyList(),
                        mutualFollowCount = dp.mutual_count,
                        mutualFollowName  = mutualFollowName,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SuggestedPlaceholderSection(title: String) {
    Column {
        Text(
            text       = title,
            fontSize   = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color      = OlasColors.Text1,
            modifier   = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding        = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(3) {
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OlasColors.Surface),
                )
            }
        }
    }
}
