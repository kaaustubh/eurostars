package com.sensars.eurostars.ui.screens.patient.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class PatientTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    WalkMode("walk_mode", "Walk Mode", Icons.Default.Home),
    GaitAnalysis("gait_analysis", "Gait Analysis", Icons.Default.BarChart),
    Pairing("pairing", "Pairing", Icons.Default.Settings)
}

