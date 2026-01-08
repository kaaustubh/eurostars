package com.sensoria.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import com.sensoria.app.data.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Composable
fun SplashRoute(
    readRole: () -> Flow<UserRole?>,
    onPatient: () -> Unit,
    onClinician: () -> Unit,
    onNoRole: () -> Unit
) {
    LaunchedEffect(Unit) {
        val role = readRole().first()
        when (role) {
            UserRole.PATIENT -> onPatient()
            UserRole.CLINICIAN -> onClinician()
            null -> onNoRole()
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
}
