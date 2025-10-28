package com.sensars.eurostars.data

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.sensars.eurostars.data.model.Patient

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
}
