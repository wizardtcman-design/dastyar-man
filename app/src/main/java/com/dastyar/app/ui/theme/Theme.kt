package com.dastyar.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.dastyar.app.R

val Vazir = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

// Brand palette
val Purple = Color(0xFF8B5CF6)
val Pink = Color(0xFFEC4899)
val Cyan = Color(0xFF22D3EE)
val Amber = Color(0xFFFBBF24)
val Rose = Color(0xFFFB7185)
val Green = Color(0xFF34D399)

private val DarkColors = darkColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    secondary = Pink,
    tertiary = Cyan,
    background = Color(0xFF0F1020),
    onBackground = Color(0xFFEDE9FE),
    surface = Color(0xFF1A1830),
    onSurface = Color(0xFFEDE9FE),
    surfaceVariant = Color(0xFF262347),
    onSurfaceVariant = Color(0xFFC9C6E4),
    outline = Color(0xFF4A4470),
    error = Color(0xFFF87171)
)

private val LightColors = lightColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    secondary = Pink,
    tertiary = Color(0xFF0891B2),
    background = Color(0xFFF7F5FF),
    onBackground = Color(0xFF1B1533),
    surface = Color.White,
    onSurface = Color(0xFF1B1533),
    surfaceVariant = Color(0xFFEDE9FE),
    onSurfaceVariant = Color(0xFF4C4677),
    outline = Color(0xFFC4B5FD),
    error = Color(0xFFDC2626)
)

@Composable
fun DastyarTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography().withFont(),
        content = content
    )
}

private fun Typography.withFont() = Typography(
    displayLarge = displayLarge.copy(fontFamily = Vazir),
    displayMedium = displayMedium.copy(fontFamily = Vazir),
    displaySmall = displaySmall.copy(fontFamily = Vazir),
    headlineLarge = headlineLarge.copy(fontFamily = Vazir),
    headlineMedium = headlineMedium.copy(fontFamily = Vazir),
    headlineSmall = headlineSmall.copy(fontFamily = Vazir),
    titleLarge = titleLarge.copy(fontFamily = Vazir),
    titleMedium = titleMedium.copy(fontFamily = Vazir),
    titleSmall = titleSmall.copy(fontFamily = Vazir),
    bodyLarge = bodyLarge.copy(fontFamily = Vazir),
    bodyMedium = bodyMedium.copy(fontFamily = Vazir),
    bodySmall = bodySmall.copy(fontFamily = Vazir),
    labelLarge = labelLarge.copy(fontFamily = Vazir),
    labelMedium = labelMedium.copy(fontFamily = Vazir),
    labelSmall = labelSmall.copy(fontFamily = Vazir)
)
