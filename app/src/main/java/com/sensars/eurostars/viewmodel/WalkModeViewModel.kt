package com.sensars.eurostars.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.sensars.eurostars.EurostarsApp
import com.sensars.eurostars.data.SessionRepository
import com.sensars.eurostars.data.WalkModeRepository
import com.sensars.eurostars.data.ble.PressureSample
import com.sensars.eurostars.data.ble.SensorConnectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * ViewModel for managing Walk Mode sessions.
 * Observes sensor data streams, buffers data locally, and handles Firestore upload.
 */
class WalkModeViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionRepo = SessionRepository(application)
    private val walkModeRepo = WalkModeRepository()
    private val connectionManager: SensorConnectionManager = (application as EurostarsApp).sensorConnectionManager
    private val dataHandler = connectionManager.getDataHandler()

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

    // Pressure data flows for UI (heatmap display)
    private val _leftPressureData = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val leftPressureData: StateFlow<Map<Int, Long>> = _leftPressureData.asStateFlow()

    private val _rightPressureData = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val rightPressureData: StateFlow<Map<Int, Long>> = _rightPressureData.asStateFlow()

    init {
        // Start collecting pressure data continuously
        viewModelScope.launch {
            try {
                dataHandler.getUnifiedPressureFlow()
                    .collect { sample ->
                    // Only process if walk mode is active
                    if (_isWalkModeActive.value) {
                        // Buffer for upload
                        walkModeRepo.addPressureSample(sample.sensorSide, sample)

                        // Update UI data (aggregate by taxel index, keep latest value)
                        when (sample.sensorSide) {
                            PairingTarget.LEFT_SENSOR -> {
                                _leftPressureData.value = _leftPressureData.value.toMutableMap().apply {
                                    put(sample.taxelIndex, sample.value)
                                }
                            }
                            PairingTarget.RIGHT_SENSOR -> {
                                _rightPressureData.value = _rightPressureData.value.toMutableMap().apply {
                                    put(sample.taxelIndex, sample.value)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uploadError.value = "Error receiving sensor data: ${e.message}"
            }
        }
    }

    /**
     * Start Walk Mode session.
     */
    fun startWalkMode() {
        if (_isWalkModeActive.value) return

        _isWalkModeActive.value = true
        _sessionStartTime.value = LocalDateTime.now()
        _sessionEndTime.value = null
        _uploadError.value = null
        walkModeRepo.clearBuffer()
        _leftPressureData.value = emptyMap()
        _rightPressureData.value = emptyMap()
    }

    /**
     * Stop Walk Mode session and upload data.
     */
    fun stopWalkMode() {
        if (!_isWalkModeActive.value) return

        _isWalkModeActive.value = false
        _sessionEndTime.value = LocalDateTime.now()

        // Upload session data
        viewModelScope.launch {
            val session = sessionRepo.sessionFlow.first()
            val patientId = session.patientId
            if (patientId.isEmpty()) {
                _uploadError.value = "Patient ID not found. Cannot upload session."
                return@launch
            }

            val startTime = _sessionStartTime.value
            val endTime = _sessionEndTime.value

            if (startTime == null || endTime == null) {
                _uploadError.value = "Invalid session times. Cannot upload."
                return@launch
            }

            _uploading.value = true
            _uploadError.value = null

            val startTimestamp = Timestamp(startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() / 1000, 0)
            val endTimestamp = Timestamp(endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() / 1000, 0)

            walkModeRepo.uploadSession(
                patientId = patientId,
                sessionStartTime = startTimestamp,
                sessionEndTime = endTimestamp,
                onSuccess = {
                    _uploading.value = false
                    _leftPressureData.value = emptyMap()
                    _rightPressureData.value = emptyMap()
                },
                onError = { error ->
                    _uploading.value = false
                    _uploadError.value = error
                }
            )
        }
    }

    /**
     * Clear upload error.
     */
    fun clearUploadError() {
        _uploadError.value = null
    }

    /**
     * Get buffer size for monitoring.
     */
    fun getBufferSize(): Int = walkModeRepo.getBufferSize()
}

