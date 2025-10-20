package com.sensars.eurostars.ui.screens.clinician.dashboard

import androidx.annotation.DrawableRes
import com.sensars.eurostars.R
import com.sensars.eurostars.R.drawable.ic_dashboard
import com.sensars.eurostars.R.drawable.ic_group
import com.sensars.eurostars.R.drawable.ic_leaderboard
import com.sensars.eurostars.R.drawable.ic_settings

enum class ClinicianTab(
    val route: String,
    val label: String,
    @DrawableRes val icon: Int
) {
    Dashboard("dashboard", "Dashboard", ic_dashboard),
    Patients("patients", "Patients", ic_group),
    Reports("reports", "Reports", ic_leaderboard),
    Settings("settings", "Settings", ic_settings)
}
