package com.sensars.eurostars.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.sensars.eurostars.ui.screens.RoleSelectScreen
import com.sensars.eurostars.ui.screens.clinician.ClinicianLoginScreen
import com.sensars.eurostars.ui.screens.clinician.ClinicianSignupScreen
import com.sensars.eurostars.ui.screens.clinician.VerifyEmailScreen
import com.sensars.eurostars.ui.screens.patient.PatientLoginScreen


@Composable
fun NavHost(navController: NavHostController) {
    androidx.navigation.compose.NavHost(
        navController = navController,
        startDestination = Routes.ROLE_SELECT
    ) {
        composable(Routes.ROLE_SELECT) {
            RoleSelectScreen(
                onClinicianSelected = { navController.navigate(Routes.CLINICIAN_LOGIN) {
                    popUpTo(Routes.ROLE_SELECT) { inclusive = true }
                }},
                onPatientSelected = {
                    navController.navigate(Routes.PATIENT_LOGIN) {
                        popUpTo(Routes.ROLE_SELECT) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CLINICIAN_LOGIN) {
            ClinicianLoginScreen(
                onSignup = { navController.navigate(Routes.CLINICIAN_SIGNUP) },
                onVerifiedLogin = {
                    navController.navigate(Routes.CLINICIAN_HOME) {
                        popUpTo(Routes.CLINICIAN_LOGIN) { inclusive = true }
                    }
                },
                onNeedsVerification = {
                    navController.navigate(Routes.VERIFY_EMAIL) {
                        popUpTo(Routes.CLINICIAN_LOGIN) { inclusive = true }
                    }
                }
            )
        }

        // (Patient login)
        composable(Routes.PATIENT_LOGIN) {
            PatientLoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.PATIENT_HOME) {
                        popUpTo(Routes.PATIENT_LOGIN) { inclusive = true }
                    }
                }
            )
        }

        // (Clinician signup)
        composable(Routes.CLINICIAN_SIGNUP) {
            ClinicianSignupScreen(
                onSignupComplete = {
                    navController.navigate(Routes.VERIFY_EMAIL) {
                        popUpTo(Routes.CLINICIAN_SIGNUP) { inclusive = true }
                    }
                },
                onBackToLogin = { navController.popBackStack() }
            )
        }

        // (Verify email)
        composable(Routes.VERIFY_EMAIL) {
            VerifyEmailScreen(
                onContinue = {
                    navController.navigate(Routes.CLINICIAN_HOME) {
                        popUpTo(Routes.VERIFY_EMAIL) { inclusive = true }
                    }
                }
            )
        }
    }
}

