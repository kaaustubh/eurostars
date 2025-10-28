package com.sensars.eurostars.ui.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// === Brand Colors ===
val MainIvory      = Color(0xFFF6EFE9)
val Aquamarine     = Color(0xFF588A94)
val MainLightGreen = Color(0xFFB1C2BE)
val LightGreen100  = Color(0xFFF7F9F8) // Light green 100
val MainBrown      = Color(0xFFCDA17C)
val MainDarkBlue   = Color(0xFF3A6470)
val MainOrange     = Color(0xFFDD9178)

fun eurostarsLightColors(): ColorScheme = lightColorScheme(
    primary = Aquamarine,
    onPrimary = Color.White,
    primaryContainer = MainLightGreen,
    onPrimaryContainer = MainDarkBlue,
    secondary = MainLightGreen,
    onSecondary = MainDarkBlue,
    secondaryContainer = MainIvory,
    onSecondaryContainer = MainDarkBlue,
    tertiary = MainBrown,
    onTertiary = Color.White,
    tertiaryContainer = MainIvory,
    onTertiaryContainer = MainDarkBlue,
    background = MainIvory,
    onBackground = Color(0xFF1D2A2E),
    surface = Color.White,
    onSurface = Color(0xFF1D2A2E),
    surfaceVariant = MainLightGreen.copy(alpha = 0.25f),
    onSurfaceVariant = MainDarkBlue,
    error = MainOrange,
    onError = Color.White,
    errorContainer = MainOrange.copy(alpha = 0.15f),
    onErrorContainer = MainDarkBlue,
    outline = MainLightGreen
)

fun eurostarsDarkColors(): ColorScheme = darkColorScheme(
    primary = Aquamarine,
    onPrimary = Color.White,
    primaryContainer = MainDarkBlue,
    onPrimaryContainer = MainIvory,
    secondary = MainLightGreen,
    onSecondary = Color(0xFF0B1518),
    secondaryContainer = Color(0xFF22353B),
    onSecondaryContainer = MainIvory,
    tertiary = MainBrown,
    onTertiary = Color(0xFF2B1C11),
    tertiaryContainer = Color(0xFF3E2B1E),
    onTertiaryContainer = MainIvory,
    background = Color(0xFF0E171B),
    onBackground = Color(0xFFE8F0F2),
    surface = Color(0xFF0E171B),
    onSurface = Color(0xFFE8F0F2),
    surfaceVariant = Color(0xFF22353B),
    onSurfaceVariant = MainLightGreen,
    error = MainOrange,
    onError = Color.Black,
    errorContainer = MainOrange.copy(alpha = 0.25f),
    onErrorContainer = Color(0xFF2A0F0B),
    outline = Color(0xFF3D5258)
)

// Typography: start from Material defaults; we can override styles later if needed.
object EurostarsTypography {
    val Default: Typography = Typography()
}

// Shapes: explicit rounded corners (safer across versions)
object EurostarsShapes {
    val Default: Shapes = Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small      = RoundedCornerShape(8.dp),
        medium     = RoundedCornerShape(12.dp),
        large      = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp)
    )
}
