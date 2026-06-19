package io.f7z.olas.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.ui.theme.OlasColors
import kotlinx.coroutines.flow.filter
import org.json.JSONObject

private data class RelayEntry(val host: String, val url: String)

private val knownRelays = listOf(
    RelayEntry("relay.damus.io",  "wss://relay.damus.io"),
    RelayEntry("relay.primal.net","wss://relay.primal.net"),
    RelayEntry("nos.lol",         "wss://nos.lol"),
    RelayEntry("nostr.world",     "wss://nostr.world"),
)

@Composable
fun DiscoverScreen() {
    val suggested = remember { mutableStateListOf<SuggestedAccount>() }

    LaunchedEffect(Unit) {
        NMPBridge.nostrEvents
            .filter { json ->
                runCatching { JSONObject(json).optInt("kind") == 0 }.getOrDefault(false)
            }
            .collect { json ->
                val obj     = runCatching { JSONObject(json) }.getOrNull() ?: return@collect
                val pubkey  = obj.optString("pubkey").takeIf { it.isNotEmpty() } ?: return@collect
                val content = runCatching { JSONObject(obj.optString("content")) }.getOrNull()
                    ?: return@collect
                val name    = content.optString("display_name").takeIf { it.isNotEmpty() }
                    ?: content.optString("name").takeIf { it.isNotEmpty() }
                    ?: pubkey.take(8)
                val avatar  = content.optString("picture").takeIf { it.isNotEmpty() }
                if (suggested.none { it.pubkey == pubkey } && suggested.size < 8) {
                    suggested.add(SuggestedAccount(pubkey, name, avatar, emptyList(), 0, null))
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text       = "Suggested for you",
            fontSize   = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color      = OlasColors.Text1,
            modifier   = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))

        if (suggested.isEmpty()) {
            SuggestedPlaceholderRow()
        } else {
            LazyRow(
                contentPadding        = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(suggested, key = { it.pubkey }) { account ->
                    SuggestedAccountCard(account = account)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text       = "Browse by relay",
            fontSize   = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color      = OlasColors.Text1,
            modifier   = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))

        knownRelays.forEach { relay ->
            RelayRow(relay)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SuggestedPlaceholderRow() {
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

@Composable
private fun RelayRow(relay: RelayEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(OlasColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model              = "https://${relay.host}/favicon.ico",
                contentDescription = null,
                modifier           = Modifier.size(20.dp).clip(CircleShape),
                contentScale       = ContentScale.Crop,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(relay.host, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = OlasColors.Text1)
            Text(relay.url,  fontSize = 12.sp, color = OlasColors.Text2)
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(OlasColors.Success),
        )
    }
}
