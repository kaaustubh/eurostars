package com.sensars.eurostars.ui.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.TypographyDefaults
import androidx.compose.ui.graphics.Color

// === Brand Colors (from design_system.pdf) ===
// Base / neutrals
val MainIvory      = Color(0xFFF6EFE9) // background/surface
val Aquamarine     = Color(0xFF588A94) // brand primary (buttons, links, active)
val MainLightGreen = Color(0xFFB1C2BE) // secondary accents / outlines
val MainBrown      = Color(0xFFCDA17C) // tertiary / highlights
val MainDarkBlue   = Color(0xFF3A6470) // on-primary/headers/contrast bg
val MainOrange     = Color(0xFFDD9178) // emphasis/warnings/special CTAs

// Light scheme mapping
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
    onBackground = Color(0xFF1D2A2E),  // deep ink on ivory
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

// Dark scheme (balanced with brand hues)
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

// Typography: replace with PDF fonts once specified
object EurostarsTypography {
    val Default: Typography = TypographyDefaults.material3Typography().copy(
        // Example: set heavier titles for clinical UIs
        // titleLarge = TypographyDefaults.material3Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

// Shapes: gentle rounds to match the PDF’s soft look
object EurostarsShapes {
    val Default: Shapes = Shapes(
        extraSmall = ShapeDefaults.ExtraSmall, // 4
        small = ShapeDefaults.Small,           // 8
        medium = ShapeDefaults.Medium,         // 12
        large = ShapeDefaults.Large,           // 16
        extraLarge = ShapeDefaults.ExtraLarge  // 28
    )
}
