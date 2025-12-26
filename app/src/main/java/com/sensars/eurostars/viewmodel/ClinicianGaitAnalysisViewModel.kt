package com.sensars.eurostars.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import com.sensars.eurostars.data.WalkSession
import com.sensars.eurostars.data.PatientsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

data class CsvDataPoint(
    val timestamp: Long,
    val foot: String,
    val taxelValues: DoubleArray, // 18 taxel values in kPa
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CsvDataPoint
        if (timestamp != other.timestamp) return false
        if (foot != other.foot) return false
        return taxelValues.contentEquals(other.taxelValues)
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + foot.hashCode()
        result = 31 * result + taxelValues.contentHashCode()
        return result
    }
}

class ClinicianGaitAnalysisViewModel(
    application: Application,
    private val patientId: String,
    private val sessionStartTime: Long
) : AndroidViewModel(application) {
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val auth = FirebaseAuth.getInstance()
    private val patientsRepository = PatientsRepository()

    private val _csvData = MutableStateFlow<List<CsvDataPoint>>(emptyList())
    val csvData: StateFlow<List<CsvDataPoint>> = _csvData.asStateFlow()

    private val _grfValue = MutableStateFlow<Double?>(null)
    val grfValue: StateFlow<Double?> = _grfValue.asStateFlow()

    // Additional gait metrics
    private val _velocity = MutableStateFlow<Double?>(null)
    val velocity: StateFlow<Double?> = _velocity.asStateFlow()

    private val _cadence = MutableStateFlow<Double?>(null)
    val cadence: StateFlow<Double?> = _cadence.asStateFlow()

    private val _strideLength = MutableStateFlow<Double?>(null)
    val strideLength: StateFlow<Double?> = _strideLength.asStateFlow()

    private val _centerOfPressure = MutableStateFlow<Double?>(null)
    val centerOfPressure: StateFlow<Double?> = _centerOfPressure.asStateFlow()

    private val _lateralCenterOfMass = MutableStateFlow<Double?>(null)
    val lateralCenterOfMass: StateFlow<Double?> = _lateralCenterOfMass.asStateFlow()

    private val _extrapolatedCenterOfMass = MutableStateFlow<Double?>(null)
    val extrapolatedCenterOfMass: StateFlow<Double?> = _extrapolatedCenterOfMass.asStateFlow()

    private val _marginOfStability = MutableStateFlow<Double?>(null)
    val marginOfStability: StateFlow<Double?> = _marginOfStability.asStateFlow()

    private val _balance = MutableStateFlow<Double?>(null)
    val balance: StateFlow<Double?> = _balance.asStateFlow()

    // Store the CoP trace for visualization
    private val _copTraceLeft = MutableStateFlow<List<android.graphics.PointF>>(emptyList())
    val copTraceLeft: StateFlow<List<android.graphics.PointF>> = _copTraceLeft.asStateFlow()

    private val _copTraceRight = MutableStateFlow<List<android.graphics.PointF>>(emptyList())
    val copTraceRight: StateFlow<List<android.graphics.PointF>> = _copTraceRight.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadSessionData()
    }

    private fun loadSessionData() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                // Authenticate if needed
                if (auth.currentUser == null) {
                    auth.signInAnonymously().await()
                }

                // Fetch patient weight and height from patient profile
                val (patientWeight, patientHeight) = fetchPatientProfile()

                // Find the session by startTime
                val sessionsRef = db.collection("patients").document(patientId).collection("sessions")
                val sessionsSnapshot = sessionsRef.get().await()
                
                val session = sessionsSnapshot.documents.find { doc ->
                    val startTime = doc.getDate("startTime")?.time
                    startTime == sessionStartTime
                } ?: throw Exception("Session not found")

                val sessionId = session.getString("sessionId") ?: throw Exception("Session ID not found")
                val dataUrl = session.getString("dataUrl") ?: throw Exception("Data URL not found")

                // Download CSV file
                val csvContent = downloadCsv(dataUrl, sessionId)
                
                // Parse CSV
                val parsedData = parseCsv(csvContent)
                _csvData.value = parsedData

                // Calculate GRF with actual patient weight
                calculateGRF(parsedData, patientWeight)
                
                // Calculate Cadence (steps/min)
                calculateCadence(parsedData)
                
                // Calculate Velocity and Stride Length
                calculateVelocity(patientHeight)
                
                // Calculate Center of Pressure (CoP)
                calculateCoP(parsedData)
                
                // TODO: Calculate other metrics when formulas are available
                // For now, set placeholder values for display
                _lateralCenterOfMass.value = 0.3 // Placeholder
                _extrapolatedCenterOfMass.value = 0.4 // Placeholder
                _marginOfStability.value = 0.15 // Placeholder
                _balance.value = 0.85 // Placeholder

            } catch (e: Exception) {
                _error.value = "Failed to load session data: ${e.message}"
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun fetchPatientProfile(): Pair<Double, Double> {
        return try {
            suspendCoroutine { continuation ->
                patientsRepository.getPatientById(
                    patientId = patientId,
                    onSuccess = { patientFull ->
                        val weightKg = patientFull.weightKg?.toDouble() ?: 70.0
                        val heightCm = patientFull.heightCm?.toDouble() ?: 170.0
                        continuation.resume(Pair(weightKg, heightCm))
                    },
                    onError = { error ->
                        android.util.Log.w("ClinicianGaitAnalysisViewModel", "Failed to fetch patient profile: $error, using defaults")
                        continuation.resume(Pair(70.0, 170.0))
                    }
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("ClinicianGaitAnalysisViewModel", "Error fetching patient profile: ${e.message}, using defaults")
            Pair(70.0, 170.0)
        }
    }

    /**
     * Calculate Velocity and Stride Length
     * Formula:
     * Step length estimated from anthropometric assumptions: ~0.415 * height
     * Stride length = 2 * Step length
     * Velocity = Step length * (Cadence / 60)
     */
    private fun calculateVelocity(heightCm: Double) {
        val cadenceVal = _cadence.value ?: return
        
        // Anthropometric estimation of step length (m)
        // Average factor is ~0.415 for men, ~0.413 for women. Using 0.414 as average.
        val heightM = heightCm / 100.0
        val estimatedStepLengthM = 0.414 * heightM
        
        val velocityMps = estimatedStepLengthM * (cadenceVal / 60.0)
        val strideLengthM = estimatedStepLengthM * 2.0
        
        _velocity.value = velocityMps
        _strideLength.value = strideLengthM
        
        android.util.Log.d("ClinicianGaitAnalysisViewModel", 
            "Velocity calculation: StepLength=${String.format("%.2f", estimatedStepLengthM)}m, " +
            "Cadence=${String.format("%.2f", cadenceVal)} steps/min => " +
            "Velocity=${String.format("%.2f", velocityMps)} m/s, " +
            "StrideLength=${String.format("%.2f", strideLengthM)} m")
    }

    private suspend fun downloadCsv(url: String, sessionId: String): String {
        return try {
            // Try to get from storage reference directly using sessionId
            val storageRef = storage.reference
                .child("patients")
                .child(patientId)
                .child("sessions")
                .child("$sessionId.csv")
            
            val bytes = storageRef.getBytes(Long.MAX_VALUE).await()
            String(bytes)
        } catch (e: Exception) {
            // Fallback: try HTTP download if it's a download URL
            try {
                URL(url).openStream().use { stream ->
                    BufferedReader(InputStreamReader(stream)).use { reader ->
                        reader.readText()
                    }
                }
            } catch (e2: Exception) {
                throw Exception("Failed to download CSV: ${e.message}")
            }
        }
    }

    private fun parseCsv(csvContent: String): List<CsvDataPoint> {
        val lines = csvContent.lines()
        if (lines.isEmpty()) return emptyList()

        // Skip header line: timestamp,foot,taxel1_kpa,...,taxel18_kpa
        val dataPoints = mutableListOf<CsvDataPoint>()

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue

            val values = line.split(",")
            // Expected: timestamp, foot, taxel1_kpa, ..., taxel18_kpa (20 values total)
            if (values.size < 20) continue

            try {
                val timestamp = values[0].toLongOrNull() ?: continue
                val foot = values[1].trim()
                val taxelValues = DoubleArray(18) { index ->
                    // Values start at index 2 (after timestamp and foot)
                    values[2 + index].trim().toDoubleOrNull() ?: 0.0
                }

                // Optional IMU values (if version 3+ CSV)
                val accelX = if (values.size >= 23) values[20].trim().toFloatOrNull() ?: 0f else 0f
                val accelY = if (values.size >= 23) values[21].trim().toFloatOrNull() ?: 0f else 0f
                val accelZ = if (values.size >= 23) values[22].trim().toFloatOrNull() ?: 0f else 0f
                
                val gyroX = if (values.size >= 26) values[23].trim().toFloatOrNull() ?: 0f else 0f
                val gyroY = if (values.size >= 26) values[24].trim().toFloatOrNull() ?: 0f else 0f
                val gyroZ = if (values.size >= 26) values[25].trim().toFloatOrNull() ?: 0f else 0f

                dataPoints.add(CsvDataPoint(
                    timestamp, foot, taxelValues,
                    accelX, accelY, accelZ,
                    gyroX, gyroY, gyroZ
                ))
            } catch (e: Exception) {
                // Skip invalid lines
                continue
            }
        }

        return dataPoints
    }

    /**
     * Calculate Ground Reaction Force (GRF)
     * Formula: Sum of taxel pressures × assumed contact area, normalized to body weight
     * 
     * Assumptions:
     * - Contact area per taxel: ~1.5 cm² (0.00015 m²) - typical for pressure sensor taxels
     * - Body weight: Fetched from patient profile
     * - Pressure is in kPa, convert to Pa for force calculation
     */
    private fun calculateGRF(dataPoints: List<CsvDataPoint>, bodyWeightKg: Double) {
        if (dataPoints.isEmpty()) {
            _grfValue.value = null
            return
        }

        val bodyWeightN = bodyWeightKg * 9.81 // Convert kg to Newtons

        // Assumed contact area per taxel in m²
        val contactAreaPerTaxel = 0.00015 // 1.5 cm² = 0.00015 m²
        val totalContactArea = contactAreaPerTaxel * 18 // 18 taxels per foot

        // Calculate average GRF across all data points
        var totalForce = 0.0
        var count = 0

        dataPoints.forEach { point ->
            // Sum of all taxel pressures (in kPa)
            val sumPressureKpa = point.taxelValues.sum()
            
            // Convert kPa to Pa and calculate force
            val sumPressurePa = sumPressureKpa * 1000.0
            val force = sumPressurePa * totalContactArea // Force in Newtons
            
            totalForce += force
            count++
        }

        if (count > 0) {
            val averageForce = totalForce / count
            // Normalize to body weight (GRF as multiple of body weight)
            val grfNormalized = averageForce / bodyWeightN
            _grfValue.value = grfNormalized
        } else {
            _grfValue.value = null
        }
    }

    /**
     * Calculate Cadence (steps per minute)
     * Algorithm:
     * 1. Group data points by foot (Left/Right)
     * 2. For each foot, calculate total pressure per timestamp (sum of all taxels)
     * 3. Detect heel strikes: pressure increases from below threshold to above threshold
     * 4. Count unique steps (avoid double-counting with debouncing)
     * 5. Calculate cadence = (total steps / total time in minutes)
     * 
     * Assumptions:
     * - Pressure threshold: 5.0 kPa (minimum pressure to consider as foot contact)
     * - Minimum time between steps: 200ms (to avoid double-counting)
     * - Timestamps are in nanoseconds
     */
    private fun calculateCadence(dataPoints: List<CsvDataPoint>) {
        if (dataPoints.isEmpty()) {
            _cadence.value = null
            return
        }

        // Group by foot
        val leftFootData = dataPoints.filter { it.foot.equals("Left", ignoreCase = true) }
            .sortedBy { it.timestamp }
        val rightFootData = dataPoints.filter { it.foot.equals("Right", ignoreCase = true) }
            .sortedBy { it.timestamp }

        // Calculate total pressure per timestamp for each foot
        data class FootPressure(val timestamp: Long, val totalPressure: Double)
        
        val leftPressures = leftFootData.groupBy { it.timestamp }
            .map { (timestamp, points) ->
                FootPressure(timestamp, points.first().taxelValues.sum())
            }
            .sortedBy { it.timestamp }
        
        val rightPressures = rightFootData.groupBy { it.timestamp }
            .map { (timestamp, points) ->
                FootPressure(timestamp, points.first().taxelValues.sum())
            }
            .sortedBy { it.timestamp }

        // Detect heel strikes for each foot
        // Higher threshold to avoid false positives from static pressure
        val pressureThreshold = 20.0 // kPa - minimum pressure to consider as foot contact (increased from 5.0)
        val minStepIntervalNs = 300_000_000L // 300ms in nanoseconds (minimum time between steps)
        val minContactDurationNs = 100_000_000L // 100ms - pressure must stay above threshold for this duration
        val minReleaseDurationNs = 100_000_000L // 100ms - pressure must go below threshold for this duration
        
        fun detectSteps(pressures: List<FootPressure>): List<Long> {
            if (pressures.size < 3) return emptyList()
            
            val steps = mutableListOf<Long>()
            var lastStepTime = 0L
            var contactStartTime: Long? = null
            var releaseStartTime: Long? = null
            var previousWasAbove = pressures[0].totalPressure >= pressureThreshold
            
            for (i in 1 until pressures.size) {
                val currentPressure = pressures[i].totalPressure
                val currentTime = pressures[i].timestamp
                val currentIsAbove = currentPressure >= pressureThreshold
                
                when {
                    // Transition from below to above: start of contact
                    !previousWasAbove && currentIsAbove -> {
                        contactStartTime = currentTime
                        releaseStartTime = null
                    }
                    // Transition from above to below: end of contact, start of release
                    previousWasAbove && !currentIsAbove -> {
                        if (contactStartTime != null) {
                            releaseStartTime = currentTime
                            val contactDuration = releaseStartTime - contactStartTime
                            
                            // Check if contact duration is valid
                            if (contactDuration >= minContactDurationNs) {
                                // We have a valid contact, now wait for release duration
                                // This will be validated in the next iteration when we're still below threshold
                            } else {
                                // Contact too short, invalid step
                                contactStartTime = null
                                releaseStartTime = null
                            }
                        }
                    }
                    // Still below threshold: check if we can validate a complete step
                    !currentIsAbove && contactStartTime != null && releaseStartTime != null -> {
                        val releaseDuration = currentTime - releaseStartTime
                        
                        // Valid step: contact duration >= min AND release duration >= min
                        if (releaseDuration >= minReleaseDurationNs &&
                            (steps.isEmpty() || contactStartTime - lastStepTime >= minStepIntervalNs)) {
                            steps.add(contactStartTime)
                            lastStepTime = contactStartTime
                        }
                        
                        // Reset for next cycle
                        contactStartTime = null
                        releaseStartTime = null
                    }
                }
                
                previousWasAbove = currentIsAbove
            }
            
            // Check if there's a pending step at the end (contact started but session ended)
            if (contactStartTime != null && releaseStartTime != null) {
                val contactDuration = releaseStartTime - contactStartTime
                if (contactDuration >= minContactDurationNs &&
                    (steps.isEmpty() || contactStartTime - lastStepTime >= minStepIntervalNs)) {
                    steps.add(contactStartTime)
                }
            }
            
            return steps
        }

        val leftSteps = detectSteps(leftPressures)
        val rightSteps = detectSteps(rightPressures)
        
        // Total steps = left steps + right steps
        val totalSteps = leftSteps.size + rightSteps.size

        // Calculate session duration in minutes
        val allTimestamps = dataPoints.map { it.timestamp }
        if (allTimestamps.isEmpty()) {
            _cadence.value = null
            return
        }
        
        val minTimestamp = allTimestamps.minOrNull() ?: 0L
        val maxTimestamp = allTimestamps.maxOrNull() ?: 0L
        val durationNs = maxTimestamp - minTimestamp
        val durationMinutes = durationNs / 1_000_000_000.0 / 60.0 // Convert nanoseconds to minutes

        // Calculate cadence (steps per minute)
        if (durationMinutes > 0) {
            val cadenceValue = totalSteps / durationMinutes
            _cadence.value = cadenceValue
            android.util.Log.d("ClinicianGaitAnalysisViewModel", 
                "Cadence calculation: $totalSteps steps in ${String.format("%.2f", durationMinutes)} minutes = ${String.format("%.2f", cadenceValue)} steps/min")
        } else {
            _cadence.value = null
        }
    }

    /**
     * Calculate Center of Pressure (CoP)
     * Formula: 
     * CoPx = sum(Pi * xi) / sum(Pi)
     * CoPy = sum(Pi * yi) / sum(Pi)
     * 
     * xi, yi are the coordinates of taxel i.
     */
    private fun calculateCoP(dataPoints: List<CsvDataPoint>) {
        if (dataPoints.isEmpty()) {
            _centerOfPressure.value = null
            _copTraceLeft.value = emptyList()
            _copTraceRight.value = emptyList()
            return
        }

        // Define taxel positions (normalized 0.0 to 1.0)
        // Values from FootHeatmap.kt
        val leftTaxelPositions = listOf(
            android.graphics.PointF(0.72f, 0.19f), android.graphics.PointF(0.58f, 0.21f),
            android.graphics.PointF(0.44f, 0.24f), android.graphics.PointF(0.33f, 0.28f),
            android.graphics.PointF(0.68f, 0.33f), android.graphics.PointF(0.55f, 0.33f),
            android.graphics.PointF(0.42f, 0.35f), android.graphics.PointF(0.29f, 0.39f),
            android.graphics.PointF(0.29f, 0.47f), android.graphics.PointF(0.29f, 0.55f),
            android.graphics.PointF(0.34f, 0.66f), android.graphics.PointF(0.64f, 0.66f),
            android.graphics.PointF(0.34f, 0.75f), android.graphics.PointF(0.61f, 0.75f),
            android.graphics.PointF(0.48f, 0.83f), android.graphics.PointF(0.60f, 0.86f),
            android.graphics.PointF(0.48f, 0.90f), android.graphics.PointF(0.38f, 0.86f)
        )

        val rightTaxelPositions = leftTaxelPositions.map { 
            android.graphics.PointF(1.0f - it.x, it.y)
        }

        val traceLeft = mutableListOf<android.graphics.PointF>()
        val traceRight = mutableListOf<android.graphics.PointF>()
        
        var totalMlLeft = 0.0
        var countLeft = 0
        var totalMlRight = 0.0
        var countRight = 0

        dataPoints.forEach { point ->
            val positions = if (point.foot.equals("Left", ignoreCase = true)) leftTaxelPositions else rightTaxelPositions
            
            var sumPiXi = 0.0
            var sumPiYi = 0.0
            var sumPi = 0.0

            point.taxelValues.forEachIndexed { i, pressure ->
                if (i < positions.size) {
                    sumPiXi += pressure * positions[i].x
                    sumPiYi += pressure * positions[i].y
                    sumPi += pressure
                }
            }

            if (sumPi > 0.1) { // Avoid division by zero and noisy low pressure
                val copX = (sumPiXi / sumPi).toFloat()
                val copY = (sumPiYi / sumPi).toFloat()
                val copPoint = android.graphics.PointF(copX, copY)
                
                if (point.foot.equals("Left", ignoreCase = true)) {
                    traceLeft.add(copPoint)
                    totalMlLeft += copX
                    countLeft++
                } else {
                    traceRight.add(copPoint)
                    totalMlRight += copX
                    countRight++
                }
            }
        }

        _copTraceLeft.value = traceLeft
        _copTraceRight.value = traceRight

        // For the single "Center of pressure" metric in the chart, 
        // we'll use the average ML position (X) across both feet.
        // We'll normalize it so 0.5 is centered.
        // For right foot, x=0.5 is also center because we mirrored positions.
        val avgMlLeft = if (countLeft > 0) totalMlLeft / countLeft else 0.5
        val avgMlRight = if (countRight > 0) totalMlRight / countRight else 0.5
        
        if (countLeft > 0 && countRight > 0) {
            _centerOfPressure.value = (avgMlLeft + avgMlRight) / 2.0
        } else if (countLeft > 0) {
            _centerOfPressure.value = avgMlLeft
        } else if (countRight > 0) {
            _centerOfPressure.value = avgMlRight
        } else {
            _centerOfPressure.value = null
        }
    }
}

@Composable
fun clinicianGaitAnalysisViewModel(
    patientId: String,
    sessionStartTime: Long
): ClinicianGaitAnalysisViewModel {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    return viewModel(
        factory = ClinicianGaitAnalysisViewModelFactory(application, patientId, sessionStartTime)
    )
}

class ClinicianGaitAnalysisViewModelFactory(
    private val app: Application,
    private val patientId: String,
    private val sessionStartTime: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return ClinicianGaitAnalysisViewModel(app, patientId, sessionStartTime) as T
    }
}


