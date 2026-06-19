package io.f7z.olas.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.navigation.Routes
import io.f7z.olas.ui.theme.OlasColors

private data class ServerOption(val id: String, val name: String, val note: String, val url: String)

private val SERVER_OPTIONS = listOf(
    ServerOption("primal", "Olas Network", "Primal's Blossom infrastructure.", "https://blossom.primal.net"),
    ServerOption("satellite", "Satellite.earth", "Community-run media hosting.", "https://cdn.satellite.earth"),
    ServerOption("nostrcheck", "Nostrcheck", "Privacy-focused media hosting.", "https://nostrcheck.me"),
)

@Composable
fun MediaServerScreen(navController: NavController) {
    var selected by remember { mutableStateOf(NMPBridge.primaryBlossomServer()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProgressDots(currentStep = 1, totalSteps = 2)
        Spacer(Modifier.height(24.dp))

        Text("Where to post", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = OlasColors.Text1)
        Spacer(Modifier.height(8.dp))
        Text(
            text     = "Your photos are stored on a media server. You can change this anytime.",
            fontSize = 15.sp,
            color    = OlasColors.Text2,
        )
        Spacer(Modifier.height(32.dp))

        SERVER_OPTIONS.forEach { option ->
            val isSelected = option.url == selected
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
                    .clickable { selected = option.url }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                    RadioButton(
                        selected = isSelected,
                    onClick  = { selected = option.url },
                    colors   = RadioButtonDefaults.colors(
                        selectedColor   = OlasColors.Text1,
                        unselectedColor = OlasColors.Text3,
                    ),
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(option.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OlasColors.Text1)
                    Text(option.note, fontSize = 13.sp, color = OlasColors.Text2)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick  = {
                NMPBridge.setPrimaryBlossomServer(selected)
                navController.navigate(Routes.ONBOARDING_COMPLETE)
            },
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
