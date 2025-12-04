package com.sensars.eurostars.data

import android.content.Context
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
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
import java.io.File
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

import android.net.Uri
import com.google.firebase.storage.storage
import java.io.File

// ...

class WalkModeRepository(private val context: Context) {
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    // ...

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
            // 1. Upload Data File to Firebase Storage
            // Path: patients/{patientId}/sessions/{sessionId}.json
            // Since we don't have the final session ID yet, we use a temp one or generate one now.
            // Let's generate the Session ID first to use in the path.
            
            val sessionsRef = db.collection("patients").document(session.patientId).collection("sessions")
            
            val nextSessionId = if (session.displaySessionId == "Pending" || session.displaySessionId == "?") {
                 val snapshot = sessionsRef.get().await()
                 (snapshot.size() + 1).toString()
            } else {
                session.displaySessionId
            }
            
            val storageRef = storage.reference
                .child("patients")
                .child(session.patientId)
                .child("sessions")
                .child("$nextSessionId.json")
                
            val localFile = File(context.filesDir, session.fileName)
            if (!localFile.exists()) {
                throw Exception("Local session file not found")
            }
            
            // Upload file
            storageRef.putFile(Uri.fromFile(localFile)).await()
            
            // Get Download URL
            // Note: In some high-security rules, getting download URL might require specific permission.
            // But usually standard auth is enough.
            val downloadUrl = storageRef.downloadUrl.await().toString()

            // 2. Create Metadata Document in Firestore
            val sessionDoc = sessionsRef.document(nextSessionId)
            
            val data = hashMapOf(
                "sessionId" to nextSessionId,
                "startTime" to Date(session.startTime),
                "endTime" to Date(session.endTime),
                "dataSizeBytes" to session.dataSizeBytes,
                "dataUrl" to downloadUrl,
                "schemaVersion" to 2 // version 2 uses compact JSON and Storage
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
        val localSessions = historyManager.getSessions()
        val remoteSessions = fetchRemoteSessions()
        
        // Merge sessions
        // Map local sessions by display ID to check for duplicates
        val localMap = localSessions.associateBy { it.displaySessionId }
        val mergedList = localSessions.toMutableList()
        
        remoteSessions.forEach { remote ->
            // If we don't have a local session with this remote ID, add it
            if (!localMap.containsKey(remote.sessionId)) {
                mergedList.add(remote)
            } else {
                // If we do have it, we might want to update status if local says PENDING/UPLOADING but remote exists?
                // But for now, let's trust local state for existing ones (especially if they have fileName for retry).
                // If local is FAILED but remote exists, it means maybe it succeeded on another try?
                // For simplicity, we keep local version if it exists.
            }
        }
        
        return mergedList.sortedByDescending { it.startTime }
    }

    private suspend fun fetchRemoteSessions(): List<WalkSession> {
        val patientId = getPatientId() ?: return emptyList()
        
        try {
            // Ensure auth if needed (though getPatientId relies on session repo, not auth)
            if (auth.currentUser == null) {
                try {
                    auth.signInAnonymously().await()
                } catch (e: Exception) {
                    return emptyList()
                }
            }

            val sessionsRef = db.collection("patients").document(patientId).collection("sessions")
            val snapshot = sessionsRef.get().await()
            
            return snapshot.documents.mapNotNull { doc ->
                try {
                    val startTime = doc.getDate("startTime")?.time ?: 0L
                    val endTime = doc.getDate("endTime")?.time ?: 0L
                    val size = doc.getLong("dataSizeBytes") ?: 0L
                    
                    WalkSession(
                        sessionId = doc.id, // Use remote ID as local ID for remote-only sessions
                        displaySessionId = doc.id,
                        patientId = patientId,
                        startTime = startTime,
                        endTime = endTime,
                        status = UploadStatus.UPLOADED,
                        dataSizeBytes = size,
                        fileName = "" // No local file
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
    }
}
