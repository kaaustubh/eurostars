package com.sensars.eurostars.data

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.sensars.eurostars.data.model.Patient
import com.google.firebase.firestore.DocumentSnapshot

class PatientsRepository {
    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()

    /** Full patient payload used for editing */
    data class PatientFull(
        val patientId: String,
        val weightKg: Int?,
        val ageYears: Int?,
        val heightCm: Int?,
        val neuropathicLeg: String?,
        val dateOfLastUlcer: String?,
        val ulcerActive: String?
    )

    private fun DocumentSnapshot.toPatientFull(): PatientFull? {
        val pid = getString("patientId") ?: id
        return PatientFull(
            patientId = pid,
            weightKg = (get("weightKg") as? Number)?.toInt(),
            ageYears = (get("ageYears") as? Number)?.toInt() ?: (get("age") as? Number)?.toInt(),
            heightCm = (get("heightCm") as? Number)?.toInt(),
            neuropathicLeg = getString("neuropathicLeg"),
            dateOfLastUlcer = getString("dateOfLastUlcer"),
            ulcerActive = getString("ulcerActive")
        )
    }

    fun createPatient(
        patientId: String,
        weightKg: Int,
        ageYears: Int,
        heightCm: Int,
        neuropathicLeg: String = "",
        dateOfLastUlcer: String = "",
        ulcerActive: String = "",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
            ?: return onError("Not signed in.")

        val docRef = db.collection("patients").document(patientId)

        val now = Timestamp.now()
        val data = mutableMapOf(
            "patientId" to patientId,
            "clinicianUid" to uid,
            "weightKg" to weightKg,
            "ageYears" to ageYears,
            "heightCm" to heightCm,
            "updatedAt" to now
        )

        // Preserve existing createdAt if the document already exists
        docRef.get()
            .addOnSuccessListener { snapshot ->
                val createdAt = snapshot?.get("createdAt") as? Timestamp ?: now
                data["createdAt"] = createdAt

                if (neuropathicLeg.isNotBlank()) {
                    data["neuropathicLeg"] = neuropathicLeg
                } else if (snapshot?.contains("neuropathicLeg") == true) {
                    data["neuropathicLeg"] = snapshot.getString("neuropathicLeg") ?: ""
                }

                if (dateOfLastUlcer.isNotBlank()) {
                    data["dateOfLastUlcer"] = dateOfLastUlcer
                } else if (snapshot?.contains("dateOfLastUlcer") == true) {
                    data["dateOfLastUlcer"] = snapshot.getString("dateOfLastUlcer") ?: ""
                }

                if (ulcerActive.isNotBlank()) {
                    data["ulcerActive"] = ulcerActive
                } else if (snapshot?.contains("ulcerActive") == true) {
                    data["ulcerActive"] = snapshot.getString("ulcerActive") ?: ""
                }

                docRef.set(data)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onError(e.message ?: "Failed to save patient.") }
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Failed to prepare patient record.")
            }
    }

    /** Fetch a single patient by ID with editable fields */
    fun getPatientById(
        patientId: String,
        onSuccess: (PatientFull) -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
            ?: return onError("Not signed in.")

        val docRef = db.collection("patients").document(patientId)
        docRef.get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null && snapshot.exists()) {
                    val clinicianUid = snapshot.getString("clinicianUid")
                    if (clinicianUid != uid) {
                        onError("You don't have access to this patient.")
                    } else {
                        val full = snapshot.toPatientFull()
                        if (full != null) onSuccess(full) else onError("Invalid patient record.")
                    }
                } else {
                    onError("Patient not found.")
                }
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Failed to fetch patient.")
            }
    }

    fun getPatients(
        onSuccess: (List<Patient>) -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
            ?: return onError("Not signed in.")

        db.collection("patients")
            .whereEqualTo("clinicianUid", uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val patients = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val patientId = data["patientId"] as? String ?: doc.id
                    val age = (data["ageYears"] as? Number)?.toInt()
                        ?: (data["age"] as? Number)?.toInt()
                        ?: return@mapNotNull null
                    val originOfPain = (data["originOfPain"] as? String) ?: ""

                    Patient(
                        patientId = patientId,
                        age = age,
                        originOfPain = originOfPain
                    )
                }
                onSuccess(patients)
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Failed to fetch patients.")
            }
    }

    /**
     * Get patient data by patient ID (for patient app - no authentication required).
     * Patients can access their own data using their patient ID.
     */
    fun getPatientDataByPatientId(
        patientId: String,
        onSuccess: (PatientFull) -> Unit,
        onError: (String) -> Unit
    ) {
        val docRef = db.collection("patients").document(patientId)
        docRef.get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null && snapshot.exists()) {
                    val full = snapshot.toPatientFull()
                    if (full != null) {
                        onSuccess(full)
                    } else {
                        onError("Invalid patient record.")
                    }
                } else {
                    onError("Patient not found.")
                }
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Failed to fetch patient data.")
            }
    }
}
