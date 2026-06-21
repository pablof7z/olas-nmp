package io.f7z.olas.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.f7z.olas.ui.theme.OlasColors

private enum class SignerType(val label: String, val subtitle: String) {
    LOCAL("Local key",       "Your private key is stored on this device."),
    NIP46("NIP-46 Bunker",  "Sign remotely via a NIP-46 compatible signer."),
    NIP55("NIP-55 App",     "Delegate signing to an external signer app."),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSecurityScreen(navController: NavController) {
    var selectedSigner by rememberSaveable { mutableStateOf(SignerType.LOCAL) }

    Scaffold(
        containerColor = OlasColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Account Security",
                        fontSize   = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = OlasColors.Text1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector        = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint               = OlasColors.Text1,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OlasColors.Background,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(OlasColors.Background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionHeader("Signer Type")
                Spacer(Modifier.height(8.dp))
            }

            item {
                SignerType.values().forEach { signer ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedSigner == signer,
                            onClick  = { selectedSigner = signer },
                            colors   = RadioButtonDefaults.colors(
                                selectedColor   = OlasColors.Text1,
                                unselectedColor = OlasColors.Text3,
                            ),
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(signer.label,    fontSize = 16.sp, color = OlasColors.Text1)
                            Text(signer.subtitle, fontSize = 13.sp, color = OlasColors.Text2)
                        }
                    }
                    HorizontalDivider(
                        color     = OlasColors.Border.copy(alpha = 0.4f),
                        thickness = 0.5.dp,
                        modifier  = Modifier.padding(start = 52.dp),
                    )
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                SettingsSectionHeader("Recovery Key")
                Spacer(Modifier.height(8.dp))
            }

            item {
                TextButton(
                    onClick  = { navController.navigate(io.f7z.olas.navigation.Routes.RECOVERY_KEY) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text     = "Export Recovery Key",
                        fontSize = 16.sp,
                        color    = OlasColors.Destructive,
                    )
                }
                HorizontalDivider(color = OlasColors.Border.copy(alpha = 0.4f), thickness = 0.5.dp)
            }

            item {
                TextButton(
                    onClick  = {
                        // Tracked by https://github.com/pablof7z/olas/issues/52.
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text     = "Backup to Keystore",
                        fontSize = 16.sp,
                        color    = OlasColors.Blue,
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
