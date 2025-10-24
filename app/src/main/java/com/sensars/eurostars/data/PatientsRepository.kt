package com.sensars.eurostars.data

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore

class PatientsRepository {
    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()

    fun createPatient(
        patientId: String,
        weightKg: Int,
        ageYears: Int,
        heightCm: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
            ?: return onError("Not signed in.")

        val docRef = db.collection("patients").document(patientId)

        // Ensure unique patientId by checking existence first
        docRef.get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    onError("Patient ID already exists.")
                } else {
                    val now = Timestamp.now()
                    val data = mapOf(
                        "patientId" to patientId,
                        "clinicianUid" to uid,
                        "weightKg" to weightKg,
                        "ageYears" to ageYears,
                        "heightCm" to heightCm,
                        "createdAt" to now,
                        "updatedAt" to now
                    )
                    docRef.set(data, SetOptions.merge())
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { e -> onError(e.message ?: "Failed to save patient.") }
                }
            }
            .addOnFailureListener { e -> onError(e.message ?: "Failed to check Patient ID.") }
    }
}
