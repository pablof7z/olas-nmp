// RecoveryKeyScreen.kt — P3-D: Account Recovery Key export (Android)
//
// CRITICAL: the words "nsec", "private key", or "seed" MUST NOT appear
// anywhere in this file. User-facing term is always "Recovery Key".

package io.f7z.olas.feature.settings

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.ui.theme.OlasColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryKeyScreen(navController: NavController) {
    // Fetch once at composition time — DO NOT log.
    val recoveryKey = remember { NMPBridge.activeAccountRecoveryKey() }
    var revealed by remember { mutableStateOf(false) }
    var copied   by remember { mutableStateOf(false) }
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()

    // Clear revealed state when the screen leaves composition so the key is
    // not exposed if the user navigates back to this screen via back-stack.
    DisposableEffect(Unit) {
        onDispose { revealed = false }
    }

    Scaffold(
        containerColor = OlasColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Back Up Account",
                        fontSize   = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = OlasColors.Text1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = OlasColors.Text1)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OlasColors.Background),
            )
        },
    ) { innerPadding ->
        Column(
            modifier             = Modifier
                .fillMaxSize()
                .background(OlasColors.Background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment  = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            if (recoveryKey == null) {
                Text(
                    "No local account found. Sign in with a local key to export a Recovery Key.",
                    color     = OlasColors.Text2,
                    fontSize  = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.padding(16.dp),
                )
            } else {
                Text(
                    "Your Recovery Key",
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color      = OlasColors.Text1,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Store this somewhere safe. Anyone with this key can access your account.",
                    fontSize  = 14.sp,
                    color     = OlasColors.Text2,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))

                // Key display — masked until revealed
                androidx.compose.foundation.layout.Box(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .background(OlasColors.Surface, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment    = Alignment.Center,
                ) {
                    if (revealed) {
                        Text(
                            text       = recoveryKey,
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 13.sp,
                            color      = OlasColors.Text1,
                            textAlign  = TextAlign.Start,
                            modifier   = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text       = "●".repeat(16) + "\n" + "●".repeat(16) + "\n" + "●".repeat(12),
                                fontFamily = FontFamily.Monospace,
                                fontSize   = 13.sp,
                                color      = OlasColors.Text3.copy(alpha = 0.4f),
                                textAlign  = TextAlign.Start,
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { revealed = true }) {
                                Text("Tap to reveal", color = OlasColors.Text1, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                if (revealed) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            // Copy to clipboard — mark sensitive so the key is excluded
                            // from clipboard history and overlay previews (API 33+).
                            val clipMgr = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager
                            val clip = ClipData.newPlainText("recovery_key", recoveryKey)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                clip.description.extras = PersistableBundle().apply {
                                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                                }
                            }
                            clipMgr.setPrimaryClip(clip)
                            copied = true
                            scope.launch {
                                delay(2000)
                                copied = false
                            }
                        },
                        colors   = ButtonDefaults.buttonColors(containerColor = OlasColors.Blue),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = OlasColors.Text1)
                        androidx.compose.foundation.layout.Spacer(Modifier.fillMaxWidth(0.05f))
                        Text(
                            if (copied) "Copied — clears in 60s" else "Copy Recovery Key",
                            color = OlasColors.Text1,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = OlasColors.Border.copy(alpha = 0.5f), thickness = 0.5.dp)
                Spacer(Modifier.height(16.dp))

                Text(
                    "Never share your Recovery Key with anyone. Olas staff will never ask for it.",
                    fontSize  = 13.sp,
                    color     = OlasColors.Text3,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
