package io.f7z.olas.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.f7z.olas.navigation.Routes
import io.f7z.olas.ui.theme.OlasColors

@Composable
fun CreateAccountScreen(navController: NavController) {
    val vm: OnboardingViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.step) {
        if (state.step == OnboardingStep.FOLLOW_PACKS) {
            navController.navigate(Routes.ONBOARDING_FOLLOWS) {
                popUpTo(Routes.ONBOARDING_CREATE) { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Progress dots (step 1 of 2)
        ProgressDots(currentStep = 0, totalSteps = 2)
        Spacer(Modifier.height(32.dp))

        Text(
            text       = "Name your profile",
            fontSize   = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color      = OlasColors.Text1,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text     = "You can always change this later.",
            fontSize = 15.sp,
            color    = OlasColors.Text2,
        )
        Spacer(Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .border(1.dp, OlasColors.Border, CircleShape)
                .background(OlasColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Filled.CameraAlt,
                contentDescription = "Pick avatar",
                tint               = OlasColors.Text3,
                modifier           = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value         = state.displayName,
            onValueChange = { vm.setDisplayName(it) },
            label         = { Text("Display name") },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
        )
        Spacer(Modifier.height(16.dp))

        // Username row: @ prefix + .olas.app suffix
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "@", color = OlasColors.Text2, fontSize = 16.sp)
            androidx.compose.foundation.text.BasicTextField(
                value         = state.username,
                onValueChange = { vm.setUsername(it) },
                modifier      = Modifier.weight(1f),
                textStyle     = androidx.compose.ui.text.TextStyle(
                    color    = OlasColors.Text1,
                    fontSize = 16.sp,
                ),
                singleLine = true,
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .border(1.dp, OlasColors.Border, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        if (state.username.isEmpty()) {
                            Text("username", color = OlasColors.Text3, fontSize = 16.sp)
                        }
                        inner()
                    }
                },
            )
            Text(text = ".olas.app", color = OlasColors.Text2, fontSize = 16.sp)
        }

        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(state.error!!, color = OlasColors.Destructive, fontSize = 14.sp)
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick  = { vm.createAccount() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape    = RoundedCornerShape(12.dp),
            enabled  = state.displayName.isNotBlank() && !state.isLoading,
            colors   = ButtonDefaults.buttonColors(
                containerColor = OlasColors.Text1,
                contentColor   = OlasColors.Background,
            ),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = OlasColors.Background)
            } else {
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
fun ProgressDots(currentStep: Int, totalSteps: Int) {
    Row {
        repeat(totalSteps) { i ->
            Box(
                modifier = Modifier
                    .size(if (i == currentStep) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (i == currentStep) OlasColors.Text1 else OlasColors.Text3)
                    .padding(horizontal = 4.dp),
            )
            if (i < totalSteps - 1) Spacer(Modifier.size(8.dp))
        }
    }
}
