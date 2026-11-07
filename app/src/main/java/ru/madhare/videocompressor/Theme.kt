package ru.madhare.videocompressor

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/** Кобальт на холодной бумаге; единственный акцент — полоса «было → стало». */
private val Cobalt = Color(0xFF2543D1)
private val CobaltLight = Color(0xFF9DB0FF)

private val Light = lightColorScheme(
    primary = Cobalt,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE4FF),
    onPrimaryContainer = Color(0xFF0B1E6B),
    background = Color(0xFFF3F5F9),
    onBackground = Color(0xFF141A26),
    surface = Color.White,
    onSurface = Color(0xFF141A26),
    surfaceVariant = Color(0xFFE3E7F0),
    onSurfaceVariant = Color(0xFF515A6E),
    outline = Color(0xFFB7BECE),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = CobaltLight,
    onPrimary = Color(0xFF0B1457),
    primaryContainer = Color(0xFF1C2A7A),
    onPrimaryContainer = Color(0xFFDDE4FF),
    background = Color(0xFF0F131B),
    onBackground = Color(0xFFE6E9F1),
    surface = Color(0xFF181D28),
    onSurface = Color(0xFFE6E9F1),
    surfaceVariant = Color(0xFF283042),
    onSurfaceVariant = Color(0xFFA6AEC2),
    outline = Color(0xFF465068),
    error = Color(0xFFFFB4AB),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
