package com.notewise.tabnotes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = NotewiseBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E9FF),
    onPrimaryContainer = NotewiseBlueDark,
    secondary = CobaltPrimary,
    onSecondary = Color.White,
    background = CanvasBackground,
    onBackground = TextPrimary,
    surface = SurfaceLight,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceSubtle,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle
)

private val DarkColorScheme = darkColorScheme(
    primary = NotewiseBlueLight,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF2B3A82),
    onPrimaryContainer = Color(0xFFE3E9FF),
    secondary = NotewiseBlue,
    onSecondary = Color.White,
    background = Color(0xFF0D0F14),
    onBackground = Color(0xFFEEF0F6),
    surface = Color(0xFF1A1D25),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF2C3140),
    onSurfaceVariant = Color(0xFFB4BBCC),
    outline = Color(0xFF454B5C)
)

private val NotewiseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun NotewiseTabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = NotewiseShapes,
        content = content
    )
}
