// app/src/main/java/com/sensars/eurostars/ui/utils/WindowSize.kt
package com.sensars.eurostars.ui.utils

import android.app.Activity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberWindowWidthClass(): WindowWidthSizeClass {
    val activity = LocalContext.current as Activity
    return calculateWindowSizeClass(activity).widthSizeClass
}
