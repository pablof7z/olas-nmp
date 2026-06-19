package io.f7z.olas.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.core.OlasProfile
import io.f7z.olas.ui.theme.OlasColors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Field validation and save policy are enforced by Rust — no local constraints here.

@Composable
fun EditProfileScreen(navController: NavController, currentProfile: OlasProfile?) {
    var name        by remember { mutableStateOf(currentProfile?.name        ?: "") }
    var displayName by remember { mutableStateOf(currentProfile?.displayName ?: "") }
    var about       by remember { mutableStateOf(currentProfile?.about       ?: "") }
    var lud16       by remember { mutableStateOf(currentProfile?.lud16       ?: "") }
    var nip05       by remember { mutableStateOf(currentProfile?.nip05       ?: "") }
    var isSaving    by remember { mutableStateOf(false) }
    var saveError   by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Edit profile", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = OlasColors.Text1)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value         = displayName,
            onValueChange = { displayName = it },
            label         = { Text("Display name") },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value         = name,
            onValueChange = { name = it },
            label         = { Text("Username") },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value         = about,
            onValueChange = { about = it },
            label         = { Text("Bio") },
            modifier      = Modifier.fillMaxWidth(),
            minLines      = 3,
            maxLines      = 6,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value         = lud16,
            onValueChange = { lud16 = it },
            label         = { Text("Lightning address") },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value         = nip05,
            onValueChange = { nip05 = it },
            label         = { Text("NIP-05 identifier") },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
        )
        Spacer(Modifier.height(24.dp))
        saveError?.let {
            Text(it, color = OlasColors.Destructive, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = {
                if (isSaving) return@Button
                val fields = buildJsonObject {
                    if (name.isNotBlank()) put("name", name)
                    if (displayName.isNotBlank()) put("display_name", displayName)
                    if (about.isNotBlank()) put("about", about)
                    if (lud16.isNotBlank()) put("lud16", lud16)
                    if (nip05.isNotBlank()) put("nip05", nip05)
                    currentProfile?.picture?.let { put("picture", it) }
                    currentProfile?.banner?.let { put("banner", it) }
                }
                val actionJson = buildJsonObject {
                    put("PublishProfile", buildJsonObject { put("fields", fields) })
                }.toString()
                isSaving = true
                saveError = null
                scope.launch {
                    val terminal = NMPBridge.dispatchAndAwaitResult("nmp.publish", actionJson)
                    isSaving = false
                    if (terminal?.succeeded == true) {
                        navController.popBackStack()
                    } else {
                        saveError = "Couldn't publish profile update."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled  = !isSaving,
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = OlasColors.Text1,
                contentColor   = OlasColors.Background,
            ),
        ) {
            Text("Save", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
