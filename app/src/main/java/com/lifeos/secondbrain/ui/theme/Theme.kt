package com.lifeos.secondbrain.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lifeos.secondbrain.settings.AppearanceMode

private val Light = lightColorScheme(
    background = Color(0xFFF4F5F7),
    surface = Color(0xFFF8F9FB),
    surfaceVariant = Color(0xFFE9EBF0),
    surfaceContainer = Color(0xFFEDEFF3),
    surfaceContainerHigh = Color(0xFFE7E9EF),
    primary = Color(0xFF5367D8),
    onPrimary = Color.White,
    onBackground = Color(0xFF16171B),
    onSurface = Color(0xFF16171B),
    onSurfaceVariant = Color(0xFF686B75),
    outline = Color(0xFFC8CBD3)
)

private val Dark = darkColorScheme(
    background = Color(0xFF101115),
    surface = Color(0xFF17181D),
    surfaceVariant = Color(0xFF202127),
    surfaceContainer = Color(0xFF1C1E24),
    surfaceContainerHigh = Color(0xFF24262D),
    primary = Color(0xFFA9B5FF),
    onPrimary = Color(0xFF1B245E),
    onBackground = Color(0xFFF2F3F6),
    onSurface = Color(0xFFF2F3F6),
    onSurfaceVariant = Color(0xFFA8ABB5),
    outline = Color(0xFF3A3C44)
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 39.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.4f).sp),
    headlineSmall = TextStyle(fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2f).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.15.sp)
)

@Composable
fun SecondBrainTheme(mode: AppearanceMode = AppearanceMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        content = content
    )
}
