package io.f7z.olas.feature.onboarding

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.f7z.olas.navigation.Routes
import io.f7z.olas.ui.theme.OlasColors

@Composable
fun OnboardingCompleteScreen(navController: NavController) {
    val context = LocalContext.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 600))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Animated checkmark circle
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(OlasColors.Success.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(48.dp)) {
                    val stroke = Stroke(width = 4f, cap = StrokeCap.Round)
                    // Circle
                    drawArc(
                        color     = OlasColors.Success,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.value,
                        useCenter  = false,
                        style      = stroke,
                    )
                    // Checkmark drawn when circle is complete
                    if (progress.value > 0.8f) {
                        val alpha = ((progress.value - 0.8f) / 0.2f).coerceIn(0f, 1f)
                        drawLine(
                            color       = OlasColors.Success.copy(alpha = alpha),
                            start       = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.5f),
                            end         = androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.72f),
                            strokeWidth = 4f,
                            cap         = StrokeCap.Round,
                        )
                        drawLine(
                            color       = OlasColors.Success.copy(alpha = alpha),
                            start       = androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.72f),
                            end         = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.28f),
                            strokeWidth = 4f,
                            cap         = StrokeCap.Round,
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("You're all set!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = OlasColors.Text1)
            Spacer(Modifier.height(12.dp))
            Text(
                text     = "Your account is ready. Start sharing and following people you love.",
                fontSize = 16.sp,
                color    = OlasColors.Text2,
            )
            Spacer(Modifier.height(48.dp))

            Button(
                onClick  = {
                    context
                        .getSharedPreferences("olas_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("onboarding_complete", true)
                        .apply()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING_WELCOME) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = OlasColors.Text1,
                    contentColor   = OlasColors.Background,
                ),
            ) {
                Text(
                    "Explore Olas",
                    color = OlasColors.Background,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
