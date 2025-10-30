package com.sensars.eurostars.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sensars.eurostars.ui.screens.RoleSelectScreen
import com.sensars.eurostars.ui.screens.SplashRoute
import com.sensars.eurostars.ui.screens.clinician.ClinicianLoginScreen
import com.sensars.eurostars.ui.screens.clinician.ClinicianSignupScreen
import com.sensars.eurostars.ui.screens.clinician.VerifyEmailScreen
import com.sensars.eurostars.ui.screens.patient.PatientLoginScreen
import com.sensars.sensole.ui.screens.clinician.ClinicianHomeScreen
import com.sensars.eurostars.viewmodel.RoleViewModel
import com.sensars.eurostars.viewmodel.authViewModel


@Composable
fun NavHost(navController: NavHostController) {
    val roleVm: RoleViewModel = viewModel()
    androidx.navigation.compose.NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
        // Splash decides first screen based on persisted role
        composable(Routes.SPLASH) {
            SplashRoute(
                readRole = { roleVm.readRole() },
                onPatient = {
                    navController.navigate(Routes.PATIENT_HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onClinician = {
                    navController.navigate(Routes.CLINICIAN_HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNoRole = {
                    navController.navigate(Routes.ROLE_SELECT) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
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
        composable(Routes.CLINICIAN_HOME) {
            val authVm = authViewModel()
            ClinicianHomeScreen(
                onLogout = {
                    authVm.signOut {
                        navController.navigate(Routes.ROLE_SELECT) {
                            popUpTo(Routes.CLINICIAN_HOME) { inclusive = true }
                        }
                    }
                }
            )
        }
    }
}

