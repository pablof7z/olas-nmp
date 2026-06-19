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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.f7z.olas.core.WOT_GAP_SETTINGS_NOTE
import io.f7z.olas.ui.theme.OlasColors

private data class WoTPreset(val id: String, val name: String, val description: String)

private val WOT_PRESETS = listOf(
    WoTPreset("close",    "Close",    "Just the people you follow and those they follow closely."),
    WoTPreset("balanced", "Balanced", "Your broader network — friends of friends."),
    WoTPreset("open",     "Open",     "Everyone. More to discover, less filtered."),
)

@Composable
fun WoTSettingsScreen(navController: NavController) {
    var selected          by rememberSaveable { mutableStateOf("balanced") }
    var advancedExpanded  by rememberSaveable { mutableStateOf(false) }
    var minTrustScore     by rememberSaveable { mutableFloatStateOf(0.5f) }
    var hideNoConnections by rememberSaveable { mutableStateOf(true) }
    var hideNewAccounts   by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background)
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        item {
            Text(
                text     = "Who shows up in your feed.",
                fontSize = 16.sp,
                color    = OlasColors.Text2,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        item {
            WOT_PRESETS.forEach { preset ->
                WoTPresetCard(
                    preset     = preset,
                    isSelected = preset.id == selected,
                    onClick    = { selected = preset.id },
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            // WoT gap note — unified copy, mirrors iOS WoTSettingsView
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OlasColors.Surface)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector        = Icons.Filled.Info,
                        contentDescription = null,
                        tint               = OlasColors.Blue,
                        modifier           = Modifier.padding(end = 8.dp, top = 2.dp),
                    )
                    Column {
                        Text(
                            text       = "Score-based filtering coming soon",
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = OlasColors.Text1,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text     = WOT_GAP_SETTINGS_NOTE,
                            fontSize = 13.sp,
                            color    = OlasColors.Text2,
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            // Tier-3 Advanced disclosure — mirrors iOS WoTSettingsView DisclosureGroup
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OlasColors.Surface),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { advancedExpanded = !advancedExpanded }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text       = "Advanced",
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color      = OlasColors.Text2,
                            modifier   = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector        = if (advancedExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (advancedExpanded) "Collapse" else "Expand",
                            tint               = OlasColors.Text3,
                        )
                    }

                    if (advancedExpanded) {
                        HorizontalDivider(color = OlasColors.Border.copy(alpha = 0.5f), thickness = 0.5.dp)

                        // Minimum trust score slider
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text     = "Minimum trust score",
                                fontSize = 15.sp,
                                color    = OlasColors.Text2,
                            )
                            Spacer(Modifier.height(4.dp))
                            Slider(
                                value         = minTrustScore,
                                onValueChange = { minTrustScore = it },
                                valueRange    = 0f..1f,
                                colors        = SliderDefaults.colors(
                                    thumbColor               = OlasColors.Blue,
                                    activeTrackColor         = OlasColors.Blue,
                                    inactiveTrackColor       = OlasColors.Border,
                                ),
                            )
                        }

                        HorizontalDivider(color = OlasColors.Border.copy(alpha = 0.5f), thickness = 0.5.dp)

                        // Hide accounts with no connections
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text     = "Hide accounts with no connections",
                                fontSize = 15.sp,
                                color    = OlasColors.Text1,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked         = hideNoConnections,
                                onCheckedChange = { hideNoConnections = it },
                                colors          = SwitchDefaults.colors(
                                    checkedThumbColor       = OlasColors.Background,
                                    checkedTrackColor       = OlasColors.Text1,
                                    uncheckedThumbColor     = OlasColors.Text3,
                                    uncheckedTrackColor     = OlasColors.Surface2,
                                ),
                            )
                        }

                        HorizontalDivider(color = OlasColors.Border.copy(alpha = 0.5f), thickness = 0.5.dp)

                        // Hide new accounts
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text     = "Hide new accounts (< 30 days)",
                                fontSize = 15.sp,
                                color    = OlasColors.Text1,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked         = hideNewAccounts,
                                onCheckedChange = { hideNewAccounts = it },
                                colors          = SwitchDefaults.colors(
                                    checkedThumbColor       = OlasColors.Background,
                                    checkedTrackColor       = OlasColors.Text1,
                                    uncheckedThumbColor     = OlasColors.Text3,
                                    uncheckedTrackColor     = OlasColors.Surface2,
                                ),
                            )
                        }

                        HorizontalDivider(color = OlasColors.Border.copy(alpha = 0.5f), thickness = 0.5.dp)

                        // Reset to recommended
                        TextButton(
                            onClick  = {
                                selected          = "balanced"
                                minTrustScore     = 0.5f
                                hideNoConnections = true
                                hideNewAccounts   = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text     = "Reset to recommended",
                                fontSize = 15.sp,
                                color    = OlasColors.Blue,
                            )
                        }
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
            Text("✓", color = OlasColors.Text1, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
