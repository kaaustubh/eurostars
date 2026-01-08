package com.sensoria.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sensoria.app.ui.screens.clinician.patients_tab.ClinicianGaitAnalysisScreen
import com.sensoria.app.ui.screens.patient.dashboard.PatientTab
import com.sensoria.app.ui.screens.patient.gaitanalysis.GaitAnalysisScreen
import com.sensoria.app.ui.screens.patient.pairing.PairingTabScreen
import com.sensoria.app.ui.screens.patient.walkmode.WalkModeScreen

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
            GaitAnalysisScreen(
                onSessionClick = { _, _ ->
                    // Navigation to gait analysis detail screen is disabled
                    // Gait matrices feature is not yet implemented
                }
            )
        }

        composable(
            route = "gait_analysis_session/{patientId}/{sessionStartTime}",
            arguments = listOf(
                navArgument("patientId") { type = NavType.StringType },
                navArgument("sessionStartTime") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId") ?: return@composable
            val sessionStartTime = backStackEntry.arguments?.getLong("sessionStartTime") ?: return@composable
            ClinicianGaitAnalysisScreen(
                patientId = patientId,
                sessionStartTime = sessionStartTime,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(PatientTab.Pairing.route) {
            PairingTabScreen()
        }
    }
}

