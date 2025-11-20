package com.sensars.eurostars.data

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.sensars.eurostars.data.ble.PressureSample
import com.sensars.eurostars.viewmodel.PairingTarget
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing Walk Mode session data.
 * Buffers sensor data during sessions and uploads to Firestore on session end.
 */
class WalkModeRepository {
    private val db: FirebaseFirestore = Firebase.firestore

    /**
     * Session data structure for Firestore upload.
     */
    data class WalkModeSession(
        val patientId: String,
        val sessionStartTime: Timestamp,
        val sessionEndTime: Timestamp,
        val leftSensorData: List<PressureDataPoint>,
        val rightSensorData: List<PressureDataPoint>
    )

    /**
     * Pressure data point with timestamp.
     */
    data class PressureDataPoint(
        val taxelIndex: Int,
        val value: Long,
        val timestampNanos: Long,
        val timestampMillis: Long // System time in milliseconds
    )

    /**
     * Buffer for storing pressure data during a session.
     */
    private val sessionBuffer = mutableListOf<Pair<PairingTarget, PressureSample>>()

    /**
     * Add pressure sample to session buffer.
     */
    fun addPressureSample(sensorSide: PairingTarget, sample: PressureSample) {
        sessionBuffer.add(sensorSide to sample)
    }

    /**
     * Clear the session buffer.
     */
    fun clearBuffer() {
        sessionBuffer.clear()
    }

    /**
     * Upload session data to Firestore.
     * @param patientId Patient ID for the session
     * @param sessionStartTime Session start timestamp
     * @param sessionEndTime Session end timestamp
     * @param onSuccess Callback on successful upload
     * @param onError Callback on upload error
     */
    suspend fun uploadSession(
        patientId: String,
        sessionStartTime: Timestamp,
        sessionEndTime: Timestamp,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // Separate data by sensor side
            val leftData = sessionBuffer
                .filter { it.first == PairingTarget.LEFT_SENSOR }
                .map { (_, sample) ->
                    PressureDataPoint(
                        taxelIndex = sample.taxelIndex,
                        value = sample.value,
                        timestampNanos = sample.timestampNanos,
                        timestampMillis = sample.timestampNanos / 1_000_000 // Convert nanos to millis
                    )
                }

            val rightData = sessionBuffer
                .filter { it.first == PairingTarget.RIGHT_SENSOR }
                .map { (_, sample) ->
                    PressureDataPoint(
                        taxelIndex = sample.taxelIndex,
                        value = sample.value,
                        timestampNanos = sample.timestampNanos,
                        timestampMillis = sample.timestampNanos / 1_000_000
                    )
                }

            val session = WalkModeSession(
                patientId = patientId,
                sessionStartTime = sessionStartTime,
                sessionEndTime = sessionEndTime,
                leftSensorData = leftData,
                rightSensorData = rightData
            )

            // Upload to Firestore
            db.collection("walkModeSessions")
                .add(session)
                .await()

            // Clear buffer after successful upload
            clearBuffer()
            onSuccess()
        } catch (e: Exception) {
            onError("Failed to upload session: ${e.message}")
        }
    }

    /**
     * Get current buffer size (for monitoring).
     */
    fun getBufferSize(): Int = sessionBuffer.size
}

