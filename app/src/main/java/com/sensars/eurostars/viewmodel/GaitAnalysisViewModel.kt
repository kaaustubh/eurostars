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

class GaitAnalysisViewModel(application: Application) : AndroidViewModel(application) {
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
                _sessions.value = repository.getSessions()
            } catch (e: Exception) {
                _error.value = "Failed to load sessions"
            } finally {
                _loading.value = false
            }
        }
    }

    fun retryUpload(sessionId: String) {
        viewModelScope.launch {
            _loading.value = true
            repository.retryUpload(
                sessionId = sessionId,
                onSuccess = {
                    loadSessions() // Refresh list on success
                    _loading.value = false
                },
                onError = { msg ->
                    _error.value = msg
                    loadSessions() // Refresh status (it might be set to FAILED again)
                    _loading.value = false
                }
            )
        }
    }
}

class GaitAnalysisViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GaitAnalysisViewModel::class.java)) {
            return GaitAnalysisViewModel(app) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
fun gaitAnalysisViewModel(): GaitAnalysisViewModel {
    val app = LocalContext.current.applicationContext as Application
    return viewModel(factory = GaitAnalysisViewModelFactory(app))
}

