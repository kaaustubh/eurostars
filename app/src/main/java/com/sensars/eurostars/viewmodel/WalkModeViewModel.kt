package com.sensars.eurostars.viewmodel

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.sensars.eurostars.EurostarsApp
import com.sensars.eurostars.data.WalkModeRepository
import com.sensars.eurostars.data.ble.SensorConnectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * ViewModel for managing Walk Mode sessions.
 * Observes sensor data streams, buffers data locally via WalkModeRepository, and handles Firestore upload.
 */
class WalkModeViewModel(application: Application) : AndroidViewModel(application) {
    private val walkModeRepo = WalkModeRepository(application)
    private val connectionManager: SensorConnectionManager = (application as EurostarsApp).sensorConnectionManager

    private val _isWalkModeActive = MutableStateFlow(false)
    val isWalkModeActive: StateFlow<Boolean> = _isWalkModeActive.asStateFlow()

    private val _sessionStartTime = MutableStateFlow<LocalDateTime?>(null)
    val sessionStartTime: StateFlow<LocalDateTime?> = _sessionStartTime.asStateFlow()

    private val _sessionEndTime = MutableStateFlow<LocalDateTime?>(null)
    val sessionEndTime: StateFlow<LocalDateTime?> = _sessionEndTime.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    private val _uploadError = MutableStateFlow<String?>(null)
    val uploadError: StateFlow<String?> = _uploadError.asStateFlow()

    /**
     * Start Walk Mode session.
     */
    fun startWalkMode() {
        if (_isWalkModeActive.value) return

        _isWalkModeActive.value = true
        _sessionStartTime.value = LocalDateTime.now()
        _sessionEndTime.value = null
        _uploadError.value = null
        
        // Start recording in repository
        walkModeRepo.startSession(connectionManager.getDataHandler().getUnifiedStreams())
    }

    /**
     * Stop Walk Mode session and upload data.
     */
    fun stopWalkMode(save: Boolean = true, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!_isWalkModeActive.value) return

        _isWalkModeActive.value = false
        _sessionEndTime.value = LocalDateTime.now()
        _uploading.value = true

        viewModelScope.launch {
            walkModeRepo.stopSession(
                save = save,
                onSuccess = {
                    _uploading.value = false
                    onSuccess()
                },
                onError = { error ->
                    _uploading.value = false
                    _uploadError.value = error
                    onError(error)
                }
            )
        }
    }

    fun clearUploadError() {
        _uploadError.value = null
    }
}

class WalkModeViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WalkModeViewModel::class.java)) {
            return WalkModeViewModel(app) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

/** Convenience getter for Composables */
@Composable
fun walkModeViewModel(): WalkModeViewModel {
    val app = LocalContext.current.applicationContext as Application
    return viewModel(factory = WalkModeViewModelFactory(app))
}
