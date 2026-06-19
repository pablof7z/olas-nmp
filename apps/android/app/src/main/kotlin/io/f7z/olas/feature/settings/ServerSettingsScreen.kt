package io.f7z.olas.feature.settings

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import io.f7z.olas.ui.theme.OlasColors

private data class BlossomServer(
    val url: String,
    val isPrimary: Boolean,
    val connected: Boolean,
    val mirrorEnabled: Boolean,
)

@Composable
fun ServerSettingsScreen(navController: NavController) {
    val servers = remember {
        mutableStateListOf(
            BlossomServer("https://blossom.primal.net",   isPrimary = true,  connected = true,  mirrorEnabled = false),
            BlossomServer("https://blossom.satellite.earth", isPrimary = false, connected = true, mirrorEnabled = true),
        )
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var newServerUrl by remember { mutableStateOf("") }

    Scaffold(
        containerColor = OlasColors.Background,
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { showAddDialog = true },
                containerColor = OlasColors.Text1,
                contentColor   = OlasColors.Background,
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
                    text       = "Media servers",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = OlasColors.Text1,
                    modifier   = Modifier.padding(16.dp),
                )
                Text(
                    text     = "New posts will use the primary server. Existing posts stay on their original server.",
                    fontSize = 13.sp,
                    color    = OlasColors.Text2,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                )
                HorizontalDivider(color = OlasColors.Border)
            }
            items(servers) { server ->
                ServerRow(
                    server       = server,
                    onMirrorToggle = { enabled ->
                        val idx = servers.indexOf(server)
                        if (idx >= 0) servers[idx] = server.copy(mirrorEnabled = enabled)
                    },
                    onRemove = {
                        if (!server.isPrimary) servers.remove(server)
                    },
                )
                HorizontalDivider(color = OlasColors.Border.copy(alpha = 0.4f))
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title            = { Text("Add media server", color = OlasColors.Text1) },
            text             = {
                OutlinedTextField(
                    value         = newServerUrl,
                    onValueChange = { newServerUrl = it },
                    label         = { Text("https://blossom.example.com") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newServerUrl.isNotBlank()) {
                            servers.add(BlossomServer(newServerUrl.trim(), isPrimary = false, connected = false, mirrorEnabled = false))
                            newServerUrl = ""
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
private fun ServerRow(
    server: BlossomServer,
    onMirrorToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (server.connected) OlasColors.Success else OlasColors.Text3),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = server.url.removePrefix("https://"),
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color      = OlasColors.Text1,
                    )
                    if (server.isPrimary) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(OlasColors.Surface2)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text("Primary", fontSize = 10.sp, color = OlasColors.Text2, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Text(
                    if (server.connected) "Connected" else "Unreachable",
                    fontSize = 12.sp,
                    color    = if (server.connected) OlasColors.Success else OlasColors.Text3,
                )
            }
            if (!server.isPrimary) {
                TextButton(onClick = onRemove) {
                    Text("Remove", color = OlasColors.Destructive, fontSize = 13.sp)
                }
            }
        }
        if (!server.isPrimary) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mirror uploads here", fontSize = 14.sp, color = OlasColors.Text1, modifier = Modifier.weight(1f))
                Switch(
                    checked         = server.mirrorEnabled,
                    onCheckedChange = onMirrorToggle,
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor   = OlasColors.Background,
                        checkedTrackColor   = OlasColors.Text1,
                        uncheckedTrackColor = OlasColors.Surface2,
                    ),
                )
            }
        }
    }
}
