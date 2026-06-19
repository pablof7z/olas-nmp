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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.f7z.olas.navigation.Routes
import io.f7z.olas.ui.theme.OlasColors

@Composable
fun SettingsScreen(navController: NavController) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background),
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            SettingsSectionHeader("Account")
        }
        item {
            SettingsRow(icon = Icons.Filled.Person, label = "Edit profile") {}
            SettingsRow(icon = Icons.Filled.Shield, label = "Account security") {
                navController.navigate(Routes.ACCOUNT_SECURITY)
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            SettingsSectionHeader("Content & filtering")
        }
        item {
            SettingsRow(icon = Icons.Filled.Tune, label = "Web of Trust") {
                navController.navigate(Routes.WOT_SETTINGS)
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            SettingsSectionHeader("Advanced")
        }
        item {
            SettingsRow(icon = Icons.Filled.Sensors, label = "Relays") {
                navController.navigate(Routes.RELAY_SETTINGS)
            }
            SettingsRow(icon = Icons.Filled.Dns, label = "Media servers") {
                navController.navigate(Routes.SERVER_SETTINGS)
            }
            SettingsRow(icon = Icons.Filled.ElectricBolt, label = "Wallet & Zaps") {
                navController.navigate(Routes.WALLET_SETTINGS)
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text     = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color    = OlasColors.Text3,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = OlasColors.Text2)
        Text(
            text     = label,
            fontSize = 17.sp,
            color    = OlasColors.Text1,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        Icon(
            imageVector        = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint               = OlasColors.Text3,
        )
    }
    HorizontalDivider(color = OlasColors.Border.copy(alpha = 0.4f), thickness = 0.5.dp, modifier = Modifier.padding(start = 52.dp))
}
