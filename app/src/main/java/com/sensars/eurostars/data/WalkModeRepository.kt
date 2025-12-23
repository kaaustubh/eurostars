package com.sensars.eurostars.data

import android.content.Context
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import com.google.firebase.storage.StorageMetadata
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.util.Date
import java.util.UUID

/**
 * Repository for managing Walk Mode sessions.
 * Handles starting/stopping sessions, buffering data, and uploading to Firestore.
 */
class WalkModeRepository(private val context: Context) {
    private val db = Firebase.firestore
    private val storage = Firebase.storage
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
        val rawValue: Long, // Raw sensor value
        val calibratedValue: Double // Calibrated value in Pascals
    )

    fun isSessionActive(): Boolean = activeSessionId != null

    fun startSession(dataStreams: SensorDataStreams) {
        if (isSessionActive()) return
        
        activeSessionId = UUID.randomUUID().toString() 
        sessionStartTime = System.currentTimeMillis()
        sessionBuffer.leftPressure.clear()
        sessionBuffer.rightPressure.clear()
        
        // Start collecting data - store both raw and calibrated values
        collectionJob = CoroutineScope(Dispatchers.IO).launch {
            launch {
                dataStreams.pressure.collect { sample ->
                    // Store both raw and calibrated values for testing
                    // Store even if calibrated value is null (will use raw value)
                    val point = PressureDataPoint(
                        timestamp = sample.timestampNanos,
                        taxelIndex = sample.taxelIndex,
                        rawValue = sample.value,
                        calibratedValue = sample.pascalValue ?: 0.0
                    )
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

    suspend fun stopSession(save: Boolean = true, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!isSessionActive()) return
        
        collectionJob?.cancel()
        collectionJob = null
        val endTime = System.currentTimeMillis()
        
        if (!save) {
            activeSessionId = null
            onSuccess()
            return
        }

        val currentPatientId = getPatientId()
        
        if (currentPatientId.isNullOrEmpty()) {
            onError("No active patient session found")
            activeSessionId = null
            return
        }

        // Save session locally first (now saves as CSV)
        val savedSession = historyManager.saveSession(
            displaySessionId = "Pending", 
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
            // 1. Generate Session ID if needed
            val sessionsRef = db.collection("patients").document(session.patientId).collection("sessions")
            val nextSessionId = if (session.displaySessionId == "Pending" || session.displaySessionId == "?") {
                 val snapshot = sessionsRef.get().await()
                 (snapshot.size() + 1).toString()
            } else {
                session.displaySessionId
            }
            
            // 2. Upload Data File to Firebase Storage (CSV)
            val storageRef = storage.reference
                .child("patients")
                .child(session.patientId)
                .child("sessions")
                .child("$nextSessionId.csv")
                
            val localFile = File(context.filesDir, session.fileName)
            if (!localFile.exists()) {
                throw Exception("Local session file not found")
            }

            val metadata = StorageMetadata.Builder()
                .setCustomMetadata("startTime", session.startTime.toString())
                .setCustomMetadata("endTime", session.endTime.toString())
                .setCustomMetadata("sessionId", nextSessionId)
                .setContentType("text/csv")
                .build()
            
            try {
                storageRef.putFile(Uri.fromFile(localFile), metadata).await()
            } catch (e: Exception) {
                 throw Exception("File upload step failed: ${e.message}")
            }
            
            // 3. Get Download URL
            val downloadUrl = try {
                storageRef.downloadUrl.await().toString()
            } catch (e: Exception) {
                "gs://${storageRef.bucket}/${storageRef.path}"
            }

            // 4. Create Metadata Document in Firestore
            val sessionDoc = sessionsRef.document(nextSessionId)
            
            val data = hashMapOf(
                "sessionId" to nextSessionId,
                "startTime" to Date(session.startTime),
                "endTime" to Date(session.endTime),
                "dataSizeBytes" to session.dataSizeBytes,
                "dataUrl" to downloadUrl,
                "schemaVersion" to 3 // version 3 uses CSV
            )
            
            sessionDoc.set(data).await()
            
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
        emit(historyManager.getSessions())
    }
    
    suspend fun getSessions(): List<WalkSession> {
        val localSessions = historyManager.getSessions()
        val remoteSessions = fetchRemoteSessions()
        
        // Filter local sessions to exclude pending ones that might duplicate remotes
        val localMap = localSessions.associateBy { it.displaySessionId }
        val mergedList = localSessions.toMutableList()
        
        // Add only remotes that aren't already locally known by ID
        remoteSessions.forEach { remote ->
            if (!localMap.containsKey(remote.sessionId)) {
                // Also check if we have a "Pending" local session that matches this remote session's ID/Time?
                // Since we can't easily match, we rely on ID.
                // If remote list is the source of truth, we should prioritize it?
                // The user says: "I see only one session... but gait analysis shows multiple".
                // This likely means local history (sessions_meta.json) has stale/failed/duplicate entries.
                // Solution: Only show local sessions that are PENDING or FAILED. For UPLOADED, rely on remote.
                
                mergedList.add(remote)
            }
        }
        
        // Filter out local UPLOADED sessions if they exist remotely (to avoid dupes if logic fails)
        // But more importantly, filter out local sessions that claim to be UPLOADED but aren't in remote list?
        // Actually, the user's issue is likely that they have LOCAL history of previous attempts (which failed or succeeded)
        // AND remote history.
        
        // If we trust Storage as source of truth for "History", we should:
        // 1. Take all REMOTE sessions.
        // 2. Take LOCAL sessions ONLY if they are PENDING or FAILED (i.e. not yet uploaded).
        // 3. Combine them.
        
        val verifiedRemoteIds = remoteSessions.map { it.sessionId }.toSet()
        
        val finalLocalList = localSessions.filter { local ->
            // Keep if:
            // 1. It is NOT uploaded (Pending/Uploading/Failed)
            // 2. OR It IS uploaded but we just did it and it might not be in remote list yet (race condition)?
            // The safest bet for a clean list is:
            // Show all Remote.
            // Show Local ONLY if status != UPLOADED.
            local.status != UploadStatus.UPLOADED
        }
        
        return (remoteSessions + finalLocalList).sortedByDescending { it.startTime }
    }

    private suspend fun fetchRemoteSessions(): List<WalkSession> {
        val patientId = getPatientId() ?: return emptyList()
        return getRemoteSessionsForPatient(patientId)
    }

    suspend fun getRemoteSessionsForPatient(patientId: String): List<WalkSession> {
        try {
            if (auth.currentUser == null) {
                try {
                    auth.signInAnonymously().await()
                } catch (e: Exception) {
                    return emptyList()
                }
            }

            val sessionsDirRef = storage.reference
                .child("patients")
                .child(patientId)
                .child("sessions")

            val listResult = sessionsDirRef.listAll().await()
            
            return coroutineScope {
                listResult.items.map { itemRef ->
                    async {
                        try {
                            val metadata = itemRef.metadata.await()
                            
                            val startTime = metadata.getCustomMetadata("startTime")?.toLongOrNull() 
                                ?: metadata.creationTimeMillis 
                            
                            val endTime = metadata.getCustomMetadata("endTime")?.toLongOrNull() 
                                ?: metadata.creationTimeMillis
                                
                            val sessionId = metadata.getCustomMetadata("sessionId") 
                                ?: itemRef.name.replace(".json", "").replace(".csv", "")
                            
                            WalkSession(
                                sessionId = sessionId,
                                displaySessionId = sessionId,
                                patientId = patientId,
                                startTime = startTime,
                                endTime = endTime,
                                status = UploadStatus.UPLOADED,
                                dataSizeBytes = metadata.sizeBytes,
                                fileName = "" 
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

        } catch (e: Exception) {
            return emptyList()
        }
    }
}
