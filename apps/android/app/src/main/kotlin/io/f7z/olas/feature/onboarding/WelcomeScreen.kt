package io.f7z.olas.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.f7z.olas.navigation.Routes
import io.f7z.olas.ui.theme.OlasColors
import org.nmp.registry.NostrLoginBlock

@Composable
fun WelcomeScreen(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x880A0A0A), Color(0xEE0A0A0A)),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 64.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text       = "olas",
                fontSize   = 52.sp,
                fontWeight = FontWeight.Black,
                color      = OlasColors.Text1,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text     = "Your photos. Your network. No algorithm.",
                fontSize = 17.sp,
                color    = OlasColors.Text2,
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick  = { navController.navigate(Routes.ONBOARDING_CREATE) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = OlasColors.Text1,
                    contentColor   = OlasColors.Background,
                ),
            ) {
                Text("Get started", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick  = { navController.navigate(Routes.SIGN_IN) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("I have an account", color = OlasColors.Text2, fontSize = 17.sp)
            }
        }
    }
}

@Composable
fun SignInScreen(navController: NavController) {
    val vm: OnboardingViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var nsec by remember { mutableStateOf("") }
    // showManualEntry: false = show NostrLoginBlock, true = show nsec field
    var showManualEntry by remember { mutableStateOf(false) }

    LaunchedEffect(state.step) {
        if (state.step == OnboardingStep.COMPLETE) {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.ONBOARDING_WELCOME) { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = "I have an account",
                fontSize   = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color      = OlasColors.Text1,
            )
            Spacer(Modifier.height(24.dp))

            if (!showManualEntry) {
                // NMP login block: shows Amber (if installed) + "Enter your key"
                NostrLoginBlock(
                    onSignerSelected = { /* Amber / NIP-55 integration pending */ },
                    onManualKey      = { showManualEntry = true },
                    modifier         = Modifier.fillMaxWidth(),
                )
            } else {
                // Manual nsec entry — shown after tapping "Enter your key"
                OutlinedTextField(
                    value         = nsec,
                    onValueChange = { nsec = it },
                    label         = { Text("Recovery key (nsec1...)") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                )
                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.error!!, color = OlasColors.Destructive, fontSize = 14.sp)
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick  = { vm.signInNsec(nsec) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(12.dp),
                    enabled  = nsec.startsWith("nsec1") && !state.isLoading,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = OlasColors.Text1,
                        contentColor   = OlasColors.Background,
                    ),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            color    = OlasColors.Background,
                        )
                    } else {
                        Text("Sign in", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showManualEntry = false }) {
                    Text("Back", color = OlasColors.Text2)
                }
            }
        }
    }
}
