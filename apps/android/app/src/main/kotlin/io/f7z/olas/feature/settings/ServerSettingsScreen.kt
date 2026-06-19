package io.f7z.olas.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.ui.theme.OlasColors

private data class BlossomServerOption(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val recommended: Boolean,
)

private val BLOSSOM_SERVERS = listOf(
    BlossomServerOption("primal", "Olas Network", "Primal's Blossom infrastructure.", "https://blossom.primal.net", true),
    BlossomServerOption("satellite", "Satellite.earth", "Community-run media hosting.", "https://cdn.satellite.earth", false),
    BlossomServerOption("nostrcheck", "Nostrcheck", "Privacy-focused media hosting.", "https://nostrcheck.me", false),
)

@Composable
fun ServerSettingsScreen() {
    var selectedUrl by remember { mutableStateOf(NMPBridge.primaryBlossomServer()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background),
    ) {
        item {
            Text(
                text = "Media servers",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = OlasColors.Text1,
                modifier = Modifier.padding(16.dp),
            )
            Text(
                text = "New posts are uploaded to the selected Blossom server.",
                fontSize = 13.sp,
                color = OlasColors.Text2,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
            )
            HorizontalDivider(color = OlasColors.Border)
        }
        items(BLOSSOM_SERVERS, key = { it.id }) { server ->
            ServerOptionRow(
                server = server,
                selected = selectedUrl == server.url,
                onSelect = {
                    selectedUrl = server.url
                    NMPBridge.setPrimaryBlossomServer(server.url)
                },
            )
            HorizontalDivider(color = OlasColors.Border.copy(alpha = 0.4f))
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun ServerOptionRow(
    server: BlossomServerOption,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(server.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OlasColors.Text1)
                if (server.recommended) {
                    Text(
                        text = "  RECOMMENDED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OlasColors.Blue,
                    )
                }
            }
            Text(server.description, fontSize = 13.sp, color = OlasColors.Text2)
            Text(server.url, fontSize = 12.sp, color = OlasColors.Text3)
        }
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) OlasColors.Blue else OlasColors.Text3,
        )
    }
}
