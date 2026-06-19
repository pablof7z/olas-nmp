package io.f7z.olas.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.f7z.olas.ui.theme.OlasColors

private val ZAP_AMOUNTS = listOf(21, 100, 500, 1000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletSettingsScreen(navController: NavController) {
    var defaultZapAmount by rememberSaveable { mutableIntStateOf(21) }

    Scaffold(
        containerColor = OlasColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Wallet & Zaps",
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
                SettingsSectionHeader("NWC Connection")
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick  = {
                        // Tracked by https://github.com/pablof7z/olas/issues/52.
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text     = "Connect Wallet",
                        fontSize = 16.sp,
                        color    = OlasColors.Blue,
                    )
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                SettingsSectionHeader("Default Zap Amount")
                Spacer(Modifier.height(8.dp))
            }

            item {
                ZAP_AMOUNTS.forEach { amount ->
                    ZapAmountRow(
                        amount     = amount,
                        isSelected = defaultZapAmount == amount,
                        onClick    = { defaultZapAmount = amount },
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun ZapAmountRow(amount: Int, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            onClick  = onClick,
            colors   = RadioButtonDefaults.colors(
                selectedColor   = OlasColors.Text1,
                unselectedColor = OlasColors.Text3,
            ),
        )
        Text(
            text     = "$amount sats",
            fontSize = 16.sp,
            color    = OlasColors.Text1,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    HorizontalDivider(
        color     = OlasColors.Border.copy(alpha = 0.4f),
        thickness = 0.5.dp,
        modifier  = Modifier.padding(start = 52.dp),
    )
}
