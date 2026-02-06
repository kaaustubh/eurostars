package com.sensoria.app.data

import android.content.Context
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import com.google.firebase.storage.StorageMetadata
import com.sensoria.app.data.ble.SensorDataStreams
import com.sensoria.app.data.ble.SensorType
import com.sensoria.app.data.ble.sensoria.SensoriaAnalysis // Added
import com.sensoria.app.data.ble.sensoria.SensoriaSdkAdapter
import com.sensoria.app.data.ble.sensoria.SensoriaSdkAnalysis
import com.sensoria.app.data.ble.sensoria.SensoriaVerifier
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sensoria.app.util.AppLog
import com.sensoria.app.viewmodel.PairingTarget
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
import kotlinx.coroutines.SupervisorJob // Added
import java.util.concurrent.atomic.AtomicBoolean // Phase 5

class WalkModeRepository(private val context: Context) {
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val auth = FirebaseAuth.getInstance()
    private val sessionRepo = SessionRepository(context)
    private val historyManager = SessionHistoryManager(context)
    private val pairingRepo = PairingRepository(context)
    
    // Phase 4: Application-level scope for uploads to prevent cancellation
    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Phase 5: Guard against duplicate uploads
    private val isUploading = AtomicBoolean(false)
    
    private var activeSessionId: String? = null
    // ...
    private var sessionStartTime: Long = 0
    private var collectionJob: Job? = null
    
    // In-memory buffer for the current session
    private val sessionBuffer =  SessionBuffer()
    
    data class SessionBuffer(
        val leftPressure: MutableList<PressureDataPoint> = mutableListOf(),
        val rightPressure: MutableList<PressureDataPoint> = mutableListOf(),
        val leftAccel: MutableList<ImuDataPoint> = mutableListOf(),
        val rightAccel: MutableList<ImuDataPoint> = mutableListOf(),
        val leftGyro: MutableList<ImuDataPoint> = mutableListOf(),
        val rightGyro: MutableList<ImuDataPoint> = mutableListOf()
    )
    
    data class PressureDataPoint(
        val timestamp: Long,
        val taxelIndex: Int,
        val rawValue: Long, // Raw sensor value
        val calibratedValue: Double, // Calibrated value in Pascals
        val tick: Int = -1,
        val protocol: SensorType = SensorType.CURRENT
    )

    data class ImuDataPoint(
        val timestamp: Long,
        val x: Float,
        val y: Float,
        val z: Float,
        val tick: Int = -1,
        val protocol: SensorType = SensorType.CURRENT
    )

    fun isSessionActive(): Boolean = activeSessionId != null

    fun startSession(dataStreams: SensorDataStreams) {
        if (isSessionActive()) return
        
        activeSessionId = UUID.randomUUID().toString() 
        sessionStartTime = System.currentTimeMillis()
        sessionBuffer.leftPressure.clear()
        sessionBuffer.rightPressure.clear()
        sessionBuffer.leftAccel.clear()
        sessionBuffer.rightAccel.clear()
        sessionBuffer.leftGyro.clear()
        sessionBuffer.rightGyro.clear()
        
        // Start collecting data - store both raw and calibrated values
        collectionJob = CoroutineScope(Dispatchers.IO).launch {
            // Pressure collection
            launch {
                dataStreams.pressure.collect { sample ->
                    val point = PressureDataPoint(
                        timestamp = sample.timestampNanos,
                        taxelIndex = sample.taxelIndex,
                        rawValue = sample.value,
                        // For SDK data (pascalValue is null), use raw value directly
                    // For custom implementation, use pascalValue if available
                    calibratedValue = sample.pascalValue ?: (sample.value.toDouble()),
                        tick = sample.tick,
                        protocol = sample.protocol
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
            
            // Accelerometer collection
            launch {
                dataStreams.accel.collect { sample ->
                    // Only store if all components are present (not NaN)
                    if (!sample.x.isNaN() && !sample.y.isNaN() && !sample.z.isNaN()) {
                        val point = ImuDataPoint(
                            sample.timestampNanos, 
                            sample.x, sample.y, sample.z,
                            sample.tick,
                            sample.protocol
                        )
                        if (sample.sensorSide == PairingTarget.LEFT_SENSOR) {
                            synchronized(sessionBuffer.leftAccel) {
                                sessionBuffer.leftAccel.add(point)
                            }
                        } else {
                            synchronized(sessionBuffer.rightAccel) {
                                sessionBuffer.rightAccel.add(point)
                            }
                        }
                    }
                }
            }
            
            // Gyroscope collection
            launch {
                dataStreams.gyro.collect { sample ->
                    if (!sample.x.isNaN() && !sample.y.isNaN() && !sample.z.isNaN()) {
                        val point = ImuDataPoint(
                            sample.timestampNanos, 
                            sample.x, sample.y, sample.z,
                            sample.tick,
                            sample.protocol
                        )
                        if (sample.sensorSide == PairingTarget.LEFT_SENSOR) {
                            synchronized(sessionBuffer.leftGyro) {
                                sessionBuffer.leftGyro.add(point)
                            }
                        } else {
                            synchronized(sessionBuffer.rightGyro) {
                                sessionBuffer.rightGyro.add(point)
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun stopSession(save: Boolean = true, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!isSessionActive()) return
        
        val stopStartTime = System.currentTimeMillis()
        val crashlytics = FirebaseCrashlytics.getInstance()
        
        // Cancel data collection immediately
        collectionJob?.cancel()
        collectionJob = null
        val endTime = System.currentTimeMillis()
        val sessionDuration = endTime - sessionStartTime
        
        // Clear active session ID immediately so UI can respond
        val sessionIdToSave = activeSessionId
        activeSessionId = null
        
        // Log session metrics to Crashlytics
        crashlytics.setCustomKey("session_duration_ms", sessionDuration)
        crashlytics.log("Stopping walk session: duration=${sessionDuration}ms")
        
        if (!save) {
            if (SensoriaVerifier.SENSORIA_VERIFY) SensoriaVerifier.reset()
            onSuccess()
            return
        }

        // Do heavy work in background to avoid blocking UI
        try {
            val currentPatientId = getPatientId()
            
            if (currentPatientId.isNullOrEmpty()) {
                crashlytics.recordException(Exception("stopSession: No active patient session"))
                onError("No active patient session found")
                return
            }

            // Get sensor types for CSV generation
            val pairingStatus = pairingRepo.pairingStatusFlow.first()
            val leftSensorType = pairingStatus.leftSensor.sensorType
            val rightSensorType = pairingStatus.rightSensor.sensorType
            
            crashlytics.setCustomKey("left_sensor_type", leftSensorType.name)
            crashlytics.setCustomKey("right_sensor_type", rightSensorType.name)

            // Copy buffer data to avoid concurrent modification during processing
            val copyStartTime = System.currentTimeMillis()
            val leftPressureCopy = synchronized(sessionBuffer.leftPressure) {
                sessionBuffer.leftPressure.toList()
            }
            val rightPressureCopy = synchronized(sessionBuffer.rightPressure) {
                sessionBuffer.rightPressure.toList()
            }
            val leftAccelCopy = synchronized(sessionBuffer.leftAccel) {
                sessionBuffer.leftAccel.toList()
            }
            val rightAccelCopy = synchronized(sessionBuffer.rightAccel) {
                sessionBuffer.rightAccel.toList()
            }
            val leftGyroCopy = synchronized(sessionBuffer.leftGyro) {
                sessionBuffer.leftGyro.toList()
            }
            val rightGyroCopy = synchronized(sessionBuffer.rightGyro) {
                sessionBuffer.rightGyro.toList()
            }
            val copyTime = System.currentTimeMillis() - copyStartTime
            
            val totalDataPoints = leftPressureCopy.size + rightPressureCopy.size + 
                                 leftAccelCopy.size + rightAccelCopy.size + 
                                 leftGyroCopy.size + rightGyroCopy.size
            crashlytics.setCustomKey("total_data_points", totalDataPoints)
            crashlytics.setCustomKey("buffer_copy_time_ms", copyTime)

            // Save session locally first (now saves as CSV) - this is CPU intensive
            val csvStartTime = System.currentTimeMillis()
            val savedSession = historyManager.saveSession(
                displaySessionId = "Pending", 
                patientId = currentPatientId,
                startTime = sessionStartTime,
                endTime = endTime,
                leftData = leftPressureCopy,
                rightData = rightPressureCopy,
                leftAccel = leftAccelCopy,
                rightAccel = rightAccelCopy,
                leftGyro = leftGyroCopy,
                rightGyro = rightGyroCopy,
                leftSensorType = leftSensorType,
                rightSensorType = rightSensorType
            )
            val csvTime = System.currentTimeMillis() - csvStartTime
            crashlytics.setCustomKey("csv_generation_time_ms", csvTime)
            crashlytics.setCustomKey("csv_file_size_bytes", savedSession.dataSizeBytes)
            
            val totalStopTime = System.currentTimeMillis() - stopStartTime
            crashlytics.setCustomKey("total_stop_time_ms", totalStopTime)
            
            if (totalStopTime > 5000) {
                // Log warning if stop takes more than 5 seconds
                crashlytics.log("WARNING: Session stop took ${totalStopTime}ms (slow operation)")
            }

            // VERIFICATION: Print Summary and Generate Bundle
            // Skip verification files when using SDK (SDK has its own analysis)
            if (SensoriaVerifier.SENSORIA_VERIFY && !SensoriaSdkAdapter.USE_SENSORIA_SDK) {
                SensoriaVerifier.printSummary(context)
                
                // Generate additional debug files
                SensoriaVerifier.generateSummaryFile(context)
                SensoriaVerifier.generateMetadataFile(context, sessionStartTime, endTime)
                
                // Do not reset yet, reset after upload or fail
            }
            
            // ANALYSIS MODE SUMMARY
            // Skip analysis files when using SDK (SDK has its own analysis)
            if (SensoriaAnalysis.SENSORIA_ANALYSIS_MODE && !SensoriaSdkAdapter.USE_SENSORIA_SDK) {
                SensoriaAnalysis.generateIndexJson(context)
            }
            
            // SDK ANALYSIS SUMMARY (only if SDK was used)
            if (SensoriaSdkAdapter.USE_SENSORIA_SDK) {
                SensoriaSdkAnalysis.generateIndexJson(context)
                SensoriaSdkAnalysis.generateChannelSizeDump(context)
            }

            // Attempt upload (also runs in background)
            // Phase 5: Guard against duplicate uploads
            if (isUploading.compareAndSet(false, true)) {
                // Phase 4: Run upload in app-level SupervisorJob scope
                uploadScope.launch {
                    try {
                        uploadSession(savedSession, leftPressureCopy, rightPressureCopy, onSuccess, onError)
                    } finally {
                        isUploading.set(false)
                    }
                }
            } else {
                AppLog.w(context, AppLog.TAG_UPLOAD_BUNDLE, "Upload already in progress, skipping duplicate")
                onSuccess() // Call success to unblock UI
            }
        } catch (e: Exception) {
            if (SensoriaVerifier.SENSORIA_VERIFY) SensoriaVerifier.reset()
            crashlytics.recordException(e)
            crashlytics.setCustomKey("stop_session_error", e.message ?: "Unknown")
            onError("Failed to save session: ${e.message}")
        }
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

            // 2b. Upload app log snapshot (best-effort)
            val logFileName = "${nextSessionId}_log.txt"
            val logFile = AppLog.snapshotToFile(context, logFileName)
            
            // Upload Verifier Bundle if enabled (skip when using SDK)
            if (SensoriaVerifier.SENSORIA_VERIFY && !SensoriaSdkAdapter.USE_SENSORIA_SDK) {
                val sessionsRef = storage.reference
                    .child("patients")
                    .child(session.patientId)
                    .child("sessions")
                
                // 1. Raw CSV
                val rawCsv = File(context.filesDir, "sensoria_raw.csv")
                if (rawCsv.exists()) {
                    AppLog.i(context, AppLog.TAG_UPLOAD_BUNDLE, "Starting upload: sensoria_raw.csv")
                    try {
                        sessionsRef.child("${nextSessionId}_raw.csv").putFile(Uri.fromFile(rawCsv)).await()
                        AppLog.i(context, AppLog.TAG_UPLOAD_BUNDLE, "Success: sensoria_raw.csv")
                    } catch (e: Exception) {
                        AppLog.e(context, AppLog.TAG_UPLOAD_BUNDLE, "Failed: sensoria_raw.csv - ${e.message}")
                    }
                }
                
                // 2. Summary TXT
                val summary = File(context.filesDir, "verify_summary.txt")
                if (summary.exists()) {
                    AppLog.i(context, AppLog.TAG_UPLOAD_BUNDLE, "Starting upload: verify_summary.txt")
                    try {
                        sessionsRef.child("${nextSessionId}_verify_summary.txt").putFile(Uri.fromFile(summary)).await()
                        AppLog.i(context, AppLog.TAG_UPLOAD_BUNDLE, "Success: verify_summary.txt")
                    } catch (e: Exception) {
                        AppLog.e(context, AppLog.TAG_UPLOAD_BUNDLE, "Failed: verify_summary.txt - ${e.message}")
                    }
                }
                // 3. Metadata JSON
                val meta = File(context.filesDir, "debug_meta.json")
                if (meta.exists()) {
                    AppLog.i(context, AppLog.TAG_UPLOAD_BUNDLE, "Starting upload: debug_meta.json")
                    try {
                        sessionsRef.child("${nextSessionId}_debug_meta.json").putFile(Uri.fromFile(meta)).await()
                        AppLog.i(context, AppLog.TAG_UPLOAD_BUNDLE, "Success: debug_meta.json")
                    } catch (e: Exception) {
                        AppLog.e(context, AppLog.TAG_UPLOAD_BUNDLE, "Failed: debug_meta.json - ${e.message}")
                    }
                }
                
                // Phase 5: Await all uploads before resetting
                // RESET after upload is complete
                SensoriaVerifier.reset()
            }
            
            // Upload Analysis Mode Files (skip when using SDK)
            if ((SensoriaAnalysis.SENSORIA_ANALYSIS_MODE || SensoriaAnalysis.SENSORIA_CAPTURE_ALL_NOTIFY) && !SensoriaSdkAdapter.USE_SENSORIA_SDK) {
                 // Phase 3: Upload to same session folder (not analysis subfolder)
                 val sessionsRef = storage.reference
                     .child("patients")
                     .child(session.patientId)
                     .child("sessions")
                 
                 // Upload index
                 val indexFile = File(context.filesDir, "analysis_index.json")
                 if (indexFile.exists()) {
                     AppLog.i(context, AppLog.TAG_UPLOAD_BUNDLE, "Starting upload: analysis_index.json")
                     try {
                         sessionsRef.child("${nextSessionId}_analysis_index.json").putFile(Uri.fromFile(indexFile)).await()
                         AppLog.i(context, AppLog.TAG_UPLOAD_BUNDLE, "Success: analysis_index.json")
                     } catch (e: Exception) {
                         AppLog.e(context, AppLog.TAG_UPLOAD_BUNDLE, "Failed: analysis_index.json - ${e.message}")
                     }
                 }
                 
                 // Phase 3: Upload captured CSVs (char_<uuid>.csv files)
                 val capturedFiles = SensoriaAnalysis.getCapturedFiles(context)
                 capturedFiles.forEach { file ->
                     AppLog.i(context, AppLog.TAG_UPLOAD_BUNDLE, "Starting upload: ${file.name}")
                     try {
                         sessionsRef.child("${nextSessionId}_${file.name}").putFile(Uri.fromFile(file)).await()
                         AppLog.i(context, AppLog.TAG_UPLOAD_BUNDLE, "Success: ${file.name}")
                     } catch (e: Exception) {
                         AppLog.e(context, AppLog.TAG_UPLOAD_BUNDLE, "Failed: ${file.name} - ${e.message}")
                     }
                 }
                 
                 SensoriaAnalysis.reset(context)
            }
            
            // Upload SDK Analysis Index only if SDK was used
            // When SDK is enabled, skip other implementation files (verification/analysis)
            if (SensoriaSdkAdapter.USE_SENSORIA_SDK) {
                val sessionsRef = storage.reference
                    .child("patients")
                    .child(session.patientId)
                    .child("sessions")
                
                val sdkIndexFile = File(context.filesDir, "sdk_analysis_index.json")
                AppLog.i(context, AppLog.TAG_UPLOAD_BUNDLE, "sdk_analysis_index.json exists=${sdkIndexFile.exists()} size=${sdkIndexFile.length()}")
                if (sdkIndexFile.exists()) {
                    AppLog.i(context, AppLog.TAG_UPLOAD_BUNDLE, "Starting upload: sdk_analysis_index.json")
                    try {
                        sessionsRef.child("${nextSessionId}_sdk_analysis_index.json").putFile(Uri.fromFile(sdkIndexFile)).await()
                        AppLog.i(context, AppLog.TAG_UPLOAD_BUNDLE, "Success: sdk_analysis_index.json")
                    } catch (e: Exception) {
                        AppLog.e(context, AppLog.TAG_UPLOAD_BUNDLE, "Failed: sdk_analysis_index.json - ${e.message}")
                    }
                }

                val channelSizeFile = File(context.filesDir, "sdk_channel_sizes.csv")
                AppLog.i(context, AppLog.TAG_UPLOAD_BUNDLE, "sdk_channel_sizes.csv exists=${channelSizeFile.exists()} size=${channelSizeFile.length()}")
                if (channelSizeFile.exists()) {
                    AppLog.i(context, AppLog.TAG_UPLOAD_BUNDLE, "Starting upload: sdk_channel_sizes.csv")
                    try {
                        sessionsRef.child("${nextSessionId}_sdk_channel_sizes.csv").putFile(Uri.fromFile(channelSizeFile)).await()
                        AppLog.i(context, AppLog.TAG_UPLOAD_BUNDLE, "Success: sdk_channel_sizes.csv")
                    } catch (e: Exception) {
                        AppLog.e(context, AppLog.TAG_UPLOAD_BUNDLE, "Failed: sdk_channel_sizes.csv - ${e.message}")
                    }
                }
                
                SensoriaSdkAnalysis.reset(context)
            }
        
        if (logFile != null && logFile.exists() && logFile.length() > 0) {
                val logRef = storage.reference
                    .child("patients")
                    .child(session.patientId)
                    .child("sessions")
                    .child(logFileName)
                try {
                    logRef.putFile(Uri.fromFile(logFile)).await()
                } catch (_: Exception) {
                    // Best-effort: ignore log upload failures
                }
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
