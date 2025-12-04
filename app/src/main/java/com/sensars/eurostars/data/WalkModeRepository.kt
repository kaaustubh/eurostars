package com.sensars.eurostars.data

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore

import com.sensars.eurostars.data.ble.SensorDataStreams
import com.sensars.eurostars.viewmodel.PairingTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

/**
 * Repository for managing Walk Mode sessions.
 * Handles starting/stopping sessions, buffering data, and uploading to Firestore.
 */
class WalkModeRepository(private val context: Context) {
    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    private val sessionRepo = SessionRepository(context)
    
    private var activeSessionId: String? = null
    private var sessionStartTime: Long = 0
    private var collectionJob: Job? = null
    
    // In-memory buffer for the current session
    // Structure: Map of SensorSide -> Map of DataType -> List of Data
    private val sessionBuffer =  SessionBuffer()
    
    data class SessionBuffer(
        val leftPressure: MutableList<PressureDataPoint> = mutableListOf(),
        val rightPressure: MutableList<PressureDataPoint> = mutableListOf(),
        // We can add IMU data later if needed
    )
    
    data class PressureDataPoint(
        val timestamp: Long,
        val taxelIndex: Int,
        val value: Long
    )

    fun isSessionActive(): Boolean = activeSessionId != null

    fun startSession(dataStreams: SensorDataStreams) {
        if (isSessionActive()) return
        
        activeSessionId = UUID.randomUUID().toString() // Temporary ID, will use incremental for display if needed
        sessionStartTime = System.currentTimeMillis()
        sessionBuffer.leftPressure.clear()
        sessionBuffer.rightPressure.clear()
        
        // Start collecting data
        collectionJob = CoroutineScope(Dispatchers.IO).launch {
            launch {
                dataStreams.pressure.collect { sample ->
                    val point = PressureDataPoint(sample.timestampNanos, sample.taxelIndex, sample.value)
                    if (sample.sensorSide == PairingTarget.LEFT_SENSOR) {
                        synchronized(sessionBuffer.leftPressure) {
                            sessionBuffer.leftPressure.add(point)
                        }
                    } else {
                        synchronized(sessionBuffer.rightPressure) {
                            sessionBuffer.rightPressure.add(point)
                        }
                    }
                }
            }
        }
    }

    suspend fun stopSession(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!isSessionActive()) return
        
        collectionJob?.cancel()
        collectionJob = null
        val endTime = System.currentTimeMillis()
        
        // Get current patient ID and session info
        // Note: We need to get this from SessionRepository
        // For now, assume we can get it or pass it in.
        // Let's fetch it from Firestore or SessionRepo inside the upload.
        
        uploadSession(endTime, onSuccess, onError)
        
        activeSessionId = null
    }

    private suspend fun uploadSession(endTime: Long, onSuccess: () -> Unit, onError: (String) -> Unit) {
        // Get patient ID from session repo
        val session = sessionRepo.sessionFlow.first()
        val patientId = if (session.role == "patient") session.patientId else null
        
        if (patientId.isNullOrEmpty()) {
            onError("No active patient session found")
            return
        }

        try {
            // Determine Session ID (Auto-increment)
            // We simply count existing documents. Note: This is not concurrency-safe for high volume,
            // but sufficient for a single-user app instance.
            val sessionsRef = db.collection("patients").document(patientId).collection("sessions")
            val snapshot = sessionsRef.get().await()
            val nextSessionId = (snapshot.size() + 1).toString()
            
            val sessionDoc = sessionsRef.document(nextSessionId)
            
            // Prepare data
            // Mapping short keys to save space: t=timestamp, i=index, v=value
            val data = hashMapOf(
                "sessionId" to nextSessionId,
                "startTime" to Date(sessionStartTime),
                "endTime" to Date(endTime),
                "leftFootData" to sessionBuffer.leftPressure.map { 
                    mapOf("t" to it.timestamp, "i" to it.taxelIndex, "v" to it.value) 
                },
                "rightFootData" to sessionBuffer.rightPressure.map {
                    mapOf("t" to it.timestamp, "i" to it.taxelIndex, "v" to it.value)
                }
            )
            
            // Upload
            sessionDoc.set(data).await()
            onSuccess()
            
        } catch (e: Exception) {
            onError("Upload failed: ${e.message}")
        }
    }
}
