package com.sensars.eurostars.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestore
import com.sensars.eurostars.data.PatientsRepository
import com.sensars.eurostars.data.RoleRepository
import com.sensars.eurostars.data.SessionRepository
import com.sensars.eurostars.data.UserRole
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles patient ID-based login.
 * Patients log in using their Clinical Study ID that was created by a clinician.
 */
class PatientAuthViewModel(app: Application) : AndroidViewModel(app) {

    private val db = Firebase.firestore
    private val session = SessionRepository(app)
    private val roleRepo = RoleRepository(app)
    private val patientsRepo = PatientsRepository()

    /**
     * Login a patient by verifying their Clinical Study ID exists in Firestore.
     * Supports numeric matching: "0001" matches "1" etc.
     * On success, persists patient session and role.
     */
    fun loginPatient(
        patientIdRaw: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val patientId = patientIdRaw.trim()
        
        if (patientId.isEmpty()) {
            onError("Please enter your Patient Clinical Study ID")
            return
        }

        // Helper function to normalize numeric IDs (remove leading zeros)
        fun normalizeNumericId(id: String): String {
            return try {
                id.toLong().toString() // Converts "0001" -> "1", "00123" -> "123"
            } catch (e: NumberFormatException) {
                id // If not numeric, return as-is
            }
        }

        // Try exact match first (by document ID)
        val docRef = db.collection("patients").document(patientId)
        docRef.get()
            .addOnSuccessListener { snapshot ->
                try {
                    if (snapshot.exists() && snapshot.data != null) {
                        // Exact match found - use the document ID as the actual patient ID
                        val actualPatientId = snapshot.id
                        // Fetch patient data to get neuropathic leg info
                        patientsRepo.getPatientDataByPatientId(
                            patientId = actualPatientId,
                            onSuccess = { patientFull ->
                                viewModelScope.launch {
                                    try {
                                        session.setPatient(actualPatientId, patientFull.neuropathicLeg ?: "")
                                        roleRepo.setRole(UserRole.PATIENT)
                                    } catch (e: Exception) {
                                        onError("Failed to save session. Please try again.")
                                        return@launch
                                    }
                                    onSuccess()
                                }
                            },
                            onError = { errorMsg ->
                                // If fetching patient data fails, still allow login but without neuropathic leg info
                                viewModelScope.launch {
                                    try {
                                        session.setPatient(actualPatientId)
                                        roleRepo.setRole(UserRole.PATIENT)
                                    } catch (e: Exception) {
                                        onError("Failed to save session. Please try again.")
                                        return@launch
                                    }
                                    onSuccess()
                                }
                            }
                        )
                    } else {
                        // Exact match failed - try numeric matching
                        // Query all patients and match by numeric value in the patientId field
                        db.collection("patients")
                            .get()
                            .addOnSuccessListener { querySnapshot ->
                                val normalizedInput = normalizeNumericId(patientId)
                                
                                // Find a match where the stored patientId (as field or document ID) matches normalized input
                                val matchingDoc = querySnapshot.documents.firstOrNull { doc ->
                                    val docPatientId = doc.getString("patientId") ?: doc.id
                                    val normalizedDocId = normalizeNumericId(docPatientId)
                                    normalizedDocId == normalizedInput
                                }
                                
                                if (matchingDoc != null) {
                                    // Found a match with numeric comparison
                                    val actualPatientId = matchingDoc.getString("patientId") ?: matchingDoc.id
                                    // Fetch patient data to get neuropathic leg info
                                    patientsRepo.getPatientDataByPatientId(
                                        patientId = actualPatientId,
                                        onSuccess = { patientFull ->
                                            viewModelScope.launch {
                                                try {
                                                    session.setPatient(actualPatientId, patientFull.neuropathicLeg ?: "")
                                                    roleRepo.setRole(UserRole.PATIENT)
                                                } catch (e: Exception) {
                                                    onError("Failed to save session. Please try again.")
                                                    return@launch
                                                }
                                                onSuccess()
                                            }
                                        },
                                        onError = { errorMsg ->
                                            // If fetching patient data fails, still allow login but without neuropathic leg info
                                            viewModelScope.launch {
                                                try {
                                                    session.setPatient(actualPatientId)
                                                    roleRepo.setRole(UserRole.PATIENT)
                                                } catch (e: Exception) {
                                                    onError("Failed to save session. Please try again.")
                                                    return@launch
                                                }
                                                onSuccess()
                                            }
                                        }
                                    )
                                } else {
                                    // Also try normalized document ID lookup
                                    val normalizedDocRef = db.collection("patients").document(normalizedInput)
                                    normalizedDocRef.get()
                                        .addOnSuccessListener { normalizedSnapshot ->
                                            if (normalizedSnapshot.exists() && normalizedSnapshot.data != null) {
                                                val actualPatientId = normalizedSnapshot.id
                                                // Fetch patient data to get neuropathic leg info
                                                patientsRepo.getPatientDataByPatientId(
                                                    patientId = actualPatientId,
                                                    onSuccess = { patientFull ->
                                                        viewModelScope.launch {
                                                            try {
                                                                session.setPatient(actualPatientId, patientFull.neuropathicLeg ?: "")
                                                                roleRepo.setRole(UserRole.PATIENT)
                                                            } catch (e: Exception) {
                                                                onError("Failed to save session. Please try again.")
                                                                return@launch
                                                            }
                                                            onSuccess()
                                                        }
                                                    },
                                                    onError = { errorMsg ->
                                                        // If fetching patient data fails, still allow login but without neuropathic leg info
                                                        viewModelScope.launch {
                                                            try {
                                                                session.setPatient(actualPatientId)
                                                                roleRepo.setRole(UserRole.PATIENT)
                                                            } catch (e: Exception) {
                                                                onError("Failed to save session. Please try again.")
                                                                return@launch
                                                            }
                                                            onSuccess()
                                                        }
                                                    }
                                                )
                                            } else {
                                                onError("Patient ID not found. Please check your ID and try again.")
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            onError("Patient ID not found. Please check your ID and try again.")
                                        }
                                }
                            }
                            .addOnFailureListener { e ->
                                onError("Patient ID not found. Please check your ID and try again.")
                            }
                    }
                } catch (e: Exception) {
                    onError("Failed to verify Patient ID. Please try again.")
                }
            }
            .addOnFailureListener { e ->
                val errorMessage = when {
                    e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE -> 
                        "Network error. Check your connection and try again."
                    else -> "Failed to verify Patient ID. Please try again."
                }
                onError(errorMessage)
            }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            session.clear()
            roleRepo.clearRole()
        }.invokeOnCompletion { onDone() }
    }

    fun currentPatientId(): String? {
        // This would need to read from session if needed
        return null
    }
}

