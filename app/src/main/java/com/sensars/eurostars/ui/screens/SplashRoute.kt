package com.sensars.eurostars.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.flow.Flow
import com.sensars.eurostars.data.UserRole

@Composable
fun SplashRoute(
    readRole: () -> Flow<UserRole?>,
    onPatient: () -> Unit,
    onClinician: () -> Unit,
    onNoRole: () -> Unit
) {
    val role by readRole().collectAsState(initial = null as UserRole?)
    LaunchedEffect(role) {
        when (role) {
            UserRole.PATIENT   -> onPatient()
            UserRole.CLINICIAN -> onClinician()
            null               -> onNoRole()
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
}
