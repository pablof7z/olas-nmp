package io.f7z.olas.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.core.WOT_SETTINGS_NOTE
import io.f7z.olas.ui.theme.OlasColors

private data class WoTPreset(val id: String, val name: String, val description: String)

private val WOT_PRESETS = listOf(
    WoTPreset("close", "Close", "Just the people you follow and those they follow closely."),
    WoTPreset("balanced", "Balanced", "Your broader network - friends of friends."),
    WoTPreset("open", "Open", "Everyone. More to discover, less filtered."),
)

@Suppress("UNUSED_PARAMETER")
@Composable
fun WoTSettingsScreen(navController: NavController) {
    var selected by rememberSaveable { mutableStateOf(NMPBridge.wotPreset()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background)
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        item {
            Text(
                text = "Who shows up in your feed.",
                fontSize = 16.sp,
                color = OlasColors.Text2,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        item {
            WOT_PRESETS.forEach { preset ->
                WoTPresetCard(
                    preset = preset,
                    isSelected = preset.id == selected,
                    onClick = {
                        selected = preset.id
                        NMPBridge.setWotPreset(preset.id)
                    },
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OlasColors.Surface)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = OlasColors.Blue,
                        modifier = Modifier.padding(end = 8.dp, top = 2.dp),
                    )
                    Column {
                        Text(
                            text = "Trust filtering is active",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OlasColors.Text1,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = WOT_SETTINGS_NOTE,
                            fontSize = 13.sp,
                            color = OlasColors.Text2,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun WoTPresetCard(preset: WoTPreset, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OlasColors.Surface)
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) OlasColors.Text1 else OlasColors.Border,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(preset.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OlasColors.Text1)
            Spacer(Modifier.height(2.dp))
            Text(preset.description, fontSize = 14.sp, color = OlasColors.Text2)
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = OlasColors.Text1,
            )
        }
    }
}
