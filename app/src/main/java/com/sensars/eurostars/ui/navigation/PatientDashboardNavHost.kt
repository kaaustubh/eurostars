package com.sensars.eurostars.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sensars.eurostars.ui.screens.patient.dashboard.PatientTab
import com.sensars.eurostars.ui.screens.patient.gaitanalysis.GaitAnalysisScreen
import com.sensars.eurostars.ui.screens.patient.pairing.PairingTabScreen
import com.sensars.eurostars.ui.screens.patient.walkmode.WalkModeScreen

@Composable
fun PatientDashboardNavHost(
    navController: NavHostController,
    parentNavController: androidx.navigation.NavController?
) {
    NavHost(
        navController = navController,
        startDestination = PatientTab.WalkMode.route
    ) {
        composable(PatientTab.WalkMode.route) {
            WalkModeScreen(
                navController = navController,
                parentNavController = parentNavController
            )
        }
        
        composable(PatientTab.GaitAnalysis.route) {
            GaitAnalysisScreen()
        }
        
        composable(PatientTab.Pairing.route) {
            PairingTabScreen()
        }
    }
}

