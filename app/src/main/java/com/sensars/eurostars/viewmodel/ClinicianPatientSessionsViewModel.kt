package com.sensars.eurostars.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.sensars.eurostars.data.WalkModeRepository
import com.sensars.eurostars.data.WalkSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ClinicianPatientSessionsViewModel(
    application: Application,
    private val patientId: String
) : AndroidViewModel(application) {
    private val repository = WalkModeRepository(application)

    private val _sessions = MutableStateFlow<List<WalkSession>>(emptyList())
    val sessions: StateFlow<List<WalkSession>> = _sessions.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadSessions()
    }

    fun loadSessions() {
        viewModelScope.launch {
            _loading.value = true
            try {
                // For clinician, we only show remote sessions
                _sessions.value = repository.getRemoteSessionsForPatient(patientId)
                    .sortedByDescending { it.startTime }
            } catch (e: Exception) {
                _error.value = "Failed to load sessions: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }
}

class ClinicianPatientSessionsViewModelFactory(
    private val app: Application,
    private val patientId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ClinicianPatientSessionsViewModel::class.java)) {
            return ClinicianPatientSessionsViewModel(app, patientId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
fun clinicianPatientSessionsViewModel(patientId: String): ClinicianPatientSessionsViewModel {
    val app = LocalContext.current.applicationContext as Application
    return viewModel(
        key = "ClinicianPatientSessionsViewModel_$patientId", // Unique key per patient
        factory = ClinicianPatientSessionsViewModelFactory(app, patientId)
    )
}

