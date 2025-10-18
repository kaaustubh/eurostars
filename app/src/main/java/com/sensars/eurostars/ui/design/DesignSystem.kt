package com.sensars.eurostars.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
fun EurostarsTheme(
    darkTheme: Boolean = false,
    colors: ColorScheme = if (darkTheme) eurostarsDarkColors() else eurostarsLightColors(),
    typography: Typography = EurostarsTypography.Default,
    shapes: Shapes = EurostarsShapes.Default,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = shapes,
        content = content
    )
}
