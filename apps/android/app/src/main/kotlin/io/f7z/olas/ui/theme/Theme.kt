package io.f7z.olas.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

// ---- Design Tokens (dark mode only) ----
object OlasColors {
    val Background     = Color(0xFF0A0A0A)
    val Surface        = Color(0xFF161616)
    val Surface2       = Color(0xFF1E1E1E)
    val Border         = Color(0xFF2E2E2E)
    val Text1          = Color(0xFFF5F5F5)
    val Text2          = Color(0xFF999999)
    val Text3          = Color(0xFF555555)
    val Zap            = Color(0xFFFBB131)
    val Heart          = Color(0xFFFF375F)
    val Success        = Color(0xFF34C759)
    val Blue           = Color(0xFF0A84FF)
    val Destructive    = Color(0xFFFF5B54)
}

private val OlasDarkColorScheme = darkColorScheme(
    primary            = OlasColors.Text1,
    onPrimary          = OlasColors.Background,
    primaryContainer   = OlasColors.Surface2,
    onPrimaryContainer = OlasColors.Text1,
    secondary          = OlasColors.Text2,
    onSecondary        = OlasColors.Background,
    background         = OlasColors.Background,
    onBackground       = OlasColors.Text1,
    surface            = OlasColors.Surface,
    onSurface          = OlasColors.Text1,
    surfaceVariant     = OlasColors.Surface2,
    onSurfaceVariant   = OlasColors.Text2,
    outline            = OlasColors.Border,
    error              = OlasColors.Destructive,
    onError            = OlasColors.Text1,
)

val OlasTypography = Typography(
    // Titles
    displayLarge  = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Normal,  color = OlasColors.Text1),
    displayMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = OlasColors.Text1),
    displaySmall  = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = OlasColors.Text1),
    headlineMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = OlasColors.Text1),
    // Body
    bodyLarge     = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal,  color = OlasColors.Text1),
    bodyMedium    = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal,  color = OlasColors.Text1),
    bodySmall     = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal,  color = OlasColors.Text2),
    // Labels / captions
    labelLarge    = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OlasColors.Text1),
    labelMedium   = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal,  color = OlasColors.Text2),
    labelSmall    = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal,  color = OlasColors.Text3),
)

val OlasShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun OlasTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OlasDarkColorScheme,
        typography  = OlasTypography,
        shapes      = OlasShapes,
        content     = content,
    )
}
