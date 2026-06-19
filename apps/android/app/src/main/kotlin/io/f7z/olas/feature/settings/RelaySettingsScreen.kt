package io.f7z.olas.feature.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.f7z.olas.core.DefaultRelay
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.ui.theme.OlasColors
import kotlinx.serialization.json.Json

private data class RelayEntry(val url: String, val role: String, val connected: Boolean)

@Composable
fun RelaySettingsScreen(navController: NavController) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    val relays = remember {
        val defaults = runCatching {
            json.decodeFromString<List<DefaultRelay>>(NMPBridge.defaultRelaysJson())
                .map { RelayEntry(it.url, it.role, it.connected) }
        }.getOrDefault(emptyList())
        mutableStateListOf<RelayEntry>().also { it.addAll(defaults) }
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var newRelayUrl by remember { mutableStateOf("") }

    Scaffold(
        containerColor = OlasColors.Background,
        floatingActionButton = {
            FloatingActionButton(
                onClick          = { showAddDialog = true },
                containerColor   = OlasColors.Text1,
                contentColor     = OlasColors.Background,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add server")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(OlasColors.Background),
        ) {
            item {
                Text(
                    text     = "Network Servers",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color    = OlasColors.Text1,
                    modifier = Modifier.padding(16.dp),
                )
                HorizontalDivider(color = OlasColors.Border)
            }
            items(relays) { relay ->
                RelayRow(relay = relay, onRemove = { relays.remove(relay) })
                HorizontalDivider(color = OlasColors.Border.copy(alpha = 0.4f))
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title            = { Text("Add server", color = OlasColors.Text1) },
            text             = {
                OutlinedTextField(
                    value         = newRelayUrl,
                    onValueChange = { newRelayUrl = it },
                    label         = { Text("Server URL") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newRelayUrl.isNotBlank()) {
                            NMPBridge.addRelay(newRelayUrl.trim(), "both")
                            relays.add(RelayEntry(newRelayUrl.trim(), "both", false))
                            newRelayUrl = ""
                        }
                        showAddDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OlasColors.Text1, contentColor = OlasColors.Background),
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = OlasColors.Text2)
                }
            },
            containerColor = OlasColors.Surface,
        )
    }
}

@Composable
private fun RelayRow(relay: RelayEntry, onRemove: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (relay.connected) OlasColors.Success else OlasColors.Text3),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(displayServerName(relay.url), fontSize = 14.sp, color = OlasColors.Text1, fontWeight = FontWeight.Medium)
            Text(roleLabel(relay.role), fontSize = 12.sp, color = OlasColors.Text3)
        }
        TextButton(onClick = onRemove) {
            Text("Remove", color = OlasColors.Destructive, fontSize = 13.sp)
        }
    }
}

private fun displayServerName(url: String): String =
    url.removePrefix("wss://").removePrefix("ws://").removePrefix("relay.")

private fun roleLabel(role: String): String =
    if (role == "both" || role == "read-write") "Read + post" else role
