package io.f7z.olas.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.f7z.olas.core.FollowPack
import io.f7z.olas.navigation.Routes
import io.f7z.olas.ui.theme.OlasColors

private val STARTER_PACKS = listOf(
    FollowPack("1", "Photography",     "Best photographers from your network", "Photography", "#F59E0B", 32),
    FollowPack("2", "Travel",          "Wanderers sharing the world",        "Travel",      "#3B82F6", 28),
    FollowPack("3", "Food & Drink",    "Chefs, foodies, and tastemakers",    "Food",        "#EF4444", 24),
    FollowPack("4", "Art & Design",    "Illustrators and visual artists",    "Art",         "#8B5CF6", 19),
    FollowPack("5", "Open Web Builders", "People building independent social apps", "Community", "#10B981", 41),
)

@Composable
fun FollowPacksScreen(navController: NavController) {
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    val selectedCount = selected.count { it.value }
    val totalCreators = STARTER_PACKS.filter { selected[it.id] == true }.sumOf { it.count }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProgressDots(currentStep = 1, totalSteps = 2)
            Spacer(Modifier.height(24.dp))
            Text("Follow some people", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = OlasColors.Text1)
            Spacer(Modifier.height(8.dp))
            Text("Pick a few packs to see great photos right away.", fontSize = 15.sp, color = OlasColors.Text2)
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(STARTER_PACKS) { pack ->
                FollowPackCard(
                    pack     = pack,
                    enabled  = selected[pack.id] == true,
                    onToggle = { selected[pack.id] = it },
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(OlasColors.Background)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            if (selectedCount > 0) {
                Text(
                    text     = "$selectedCount packs · $totalCreators creators",
                    color    = OlasColors.Text2,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick  = { navController.navigate(Routes.ONBOARDING_COMPLETE) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = OlasColors.Text1,
                    contentColor   = OlasColors.Background,
                ),
            ) {
                Text(
                    "Continue",
                    color = OlasColors.Background,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun FollowPackCard(pack: FollowPack, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OlasColors.Surface)
            .border(
                width = if (enabled) 1.dp else 0.dp,
                color = if (enabled) OlasColors.Text1 else OlasColors.Border,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(pack.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OlasColors.Text1)
            Spacer(Modifier.height(2.dp))
            Text(pack.description, fontSize = 13.sp, color = OlasColors.Text2)
            Spacer(Modifier.height(4.dp))
            Text("${pack.count} creators", fontSize = 12.sp, color = OlasColors.Text3)
        }
        Switch(
            checked         = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor       = OlasColors.Background,
                checkedTrackColor       = OlasColors.Text1,
                uncheckedThumbColor     = OlasColors.Text3,
                uncheckedTrackColor     = OlasColors.Surface2,
            ),
        )
    }
}
