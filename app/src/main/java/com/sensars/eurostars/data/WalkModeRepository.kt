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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
    private val historyManager = SessionHistoryManager(context)
    
    private var activeSessionId: String? = null
    private var sessionStartTime: Long = 0
    private var collectionJob: Job? = null
    
    // In-memory buffer for the current session
    private val sessionBuffer =  SessionBuffer()
    
    data class SessionBuffer(
        val leftPressure: MutableList<PressureDataPoint> = mutableListOf(),
        val rightPressure: MutableList<PressureDataPoint> = mutableListOf(),
    )
    
    data class PressureDataPoint(
        val timestamp: Long,
        val taxelIndex: Int,
        val value: Long
    )

    fun isSessionActive(): Boolean = activeSessionId != null

    fun startSession(dataStreams: SensorDataStreams) {
        if (isSessionActive()) return
        
        activeSessionId = UUID.randomUUID().toString() 
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
        val currentPatientId = getPatientId()
        
        if (currentPatientId.isNullOrEmpty()) {
            onError("No active patient session found")
            activeSessionId = null
            return
        }

        // Save session locally first
        val savedSession = historyManager.saveSession(
            displaySessionId = "Pending", // Will be updated on upload
            patientId = currentPatientId,
            startTime = sessionStartTime,
            endTime = endTime,
            leftData = sessionBuffer.leftPressure,
            rightData = sessionBuffer.rightPressure
        )

        // Attempt upload
        uploadSession(savedSession, sessionBuffer.leftPressure, sessionBuffer.rightPressure, onSuccess, onError)
        
        activeSessionId = null
    }

    suspend fun retryUpload(sessionId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val sessions = historyManager.getSessions()
        val session = sessions.find { it.sessionId == sessionId } ?: return onError("Session not found")
        
        if (session.status == UploadStatus.UPLOADED) {
            onSuccess()
            return
        }
        
        val (leftData, rightData) = historyManager.getSessionData(session.fileName)
        uploadSession(session, leftData, rightData, onSuccess, onError)
    }

    private suspend fun uploadSession(
        session: WalkSession, 
        leftData: List<PressureDataPoint>, 
        rightData: List<PressureDataPoint>,
        onSuccess: () -> Unit, 
        onError: (String) -> Unit
    ) {
        historyManager.updateSessionStatus(session.sessionId, UploadStatus.UPLOADING)

        // Ensure we are authenticated
        if (auth.currentUser == null) {
            try {
                auth.signInAnonymously().await()
            } catch (e: Exception) {
                historyManager.updateSessionStatus(session.sessionId, UploadStatus.FAILED)
                onError("Authentication failed: ${e.message}")
                return
            }
        }

        try {
            // Determine remote Session ID
            val sessionsRef = db.collection("patients").document(session.patientId).collection("sessions")
            
            // If we already have a display ID (e.g. from previous partial attempt?), re-use? 
            // Ideally we get a new one or keep consistent. 
            // For now, let's always get a new one if it was "Pending" or "?".
            // If we are retrying a FAILED one, we might want to check if it already exists? 
            // Simpler: Just count again. Duplicate IDs in display might happen if we fail *after* writing but *before* confirming locally?
            // Risk: If we fail to write local status UPLOADED, we might upload duplicate. 
            // We can store the assigned remote ID in local DB.
            
            val nextSessionId = if (session.displaySessionId == "Pending" || session.displaySessionId == "?") {
                 val snapshot = sessionsRef.get().await()
                 (snapshot.size() + 1).toString()
            } else {
                session.displaySessionId
            }
            
            val sessionDoc = sessionsRef.document(nextSessionId)
            
            val data = hashMapOf(
                "sessionId" to nextSessionId,
                "startTime" to Date(session.startTime),
                "endTime" to Date(session.endTime),
                "leftFootData" to leftData.map { 
                    mapOf("t" to it.timestamp, "i" to it.taxelIndex, "v" to it.value) 
                },
                "rightFootData" to rightData.map {
                    mapOf("t" to it.timestamp, "i" to it.taxelIndex, "v" to it.value)
                }
            )
            
            sessionDoc.set(data).await()
            
            // Update local status
            historyManager.updateSessionStatus(session.sessionId, UploadStatus.UPLOADED, nextSessionId)
            onSuccess()
            
        } catch (e: Exception) {
            historyManager.updateSessionStatus(session.sessionId, UploadStatus.FAILED)
            onError("Upload failed: ${e.message}")
        }
    }

    private suspend fun getPatientId(): String? {
        val session = sessionRepo.sessionFlow.first()
        return if (session.role == "patient") session.patientId else null
    }

    fun getSessionsFlow(): Flow<List<WalkSession>> = flow {
        // Emit initial
        emit(historyManager.getSessions())
        // In a real app with Room, we'd observe the DB. 
        // Here we can just expose a method to refresh or poll.
        // For simplicity, we just emit once. The UI might need to manually refresh or we can add a broadcast mechanism.
        // Let's rely on ViewModels refreshing.
    }
    
    suspend fun getSessions(): List<WalkSession> {
        return historyManager.getSessions()
    }
}
