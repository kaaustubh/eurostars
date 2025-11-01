package com.sensars.eurostars.viewmodel

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

class PatientAuthViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PatientAuthViewModel::class.java)) {
            return PatientAuthViewModel(app) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

/** Convenience getter for Composables */
@Composable
fun patientAuthViewModel(): PatientAuthViewModel {
    val app = LocalContext.current.applicationContext as Application
    return viewModel(factory = PatientAuthViewModelFactory(app))
}

