package com.sensars.eurostars.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Create a single DataStore instance bound to the app Context
private val Context.sessionDataStore by preferencesDataStore(name = "auth_session")

// Keys
private val KEY_ROLE       = stringPreferencesKey("role")        // "clinician" | "patient"
private val KEY_EMAIL      = stringPreferencesKey("email")       // clinician email
private val KEY_PATIENT_ID = stringPreferencesKey("patient_id")  // patient Clinical Study ID
private val KEY_NEUROPATHIC_LEG = stringPreferencesKey("neuropathic_leg")  // "Left", "Right", "Both", or ""

/**
 * Minimal session persistence used by AuthViewModel / PatientAuthViewModel.
 * Stores currently active role and its identifier (email or patientId).
 */
class SessionRepository(private val context: Context) {

    data class Session(
        val role: String = "",          // "", "clinician", "patient"
        val email: String = "",         // set if role == clinician
        val patientId: String = "",      // set if role == patient
        val neuropathicLeg: String = "" // "Left", "Right", "Both", or "" (set if role == patient)
    )

    /** Observe the current session */
    val sessionFlow: Flow<Session> = context.sessionDataStore.data.map { p ->
        Session(
            role = p[KEY_ROLE] ?: "",
            email = p[KEY_EMAIL] ?: "",
            patientId = p[KEY_PATIENT_ID] ?: "",
            neuropathicLeg = p[KEY_NEUROPATHIC_LEG] ?: ""
        )
    }

    /** Set clinician session (clears patientId and neuropathic leg) */
    suspend fun setClinician(email: String) {
        context.sessionDataStore.edit {
            it[KEY_ROLE] = "clinician"
            it[KEY_EMAIL] = email
            it.remove(KEY_PATIENT_ID)
            it.remove(KEY_NEUROPATHIC_LEG)
        }
    }

    /** Set patient session (clears email) */
    suspend fun setPatient(patientId: String, neuropathicLeg: String = "") {
        context.sessionDataStore.edit {
            it[KEY_ROLE] = "patient"
            it[KEY_PATIENT_ID] = patientId
            if (neuropathicLeg.isNotBlank()) {
                it[KEY_NEUROPATHIC_LEG] = neuropathicLeg
            }
            it.remove(KEY_EMAIL)
        }
    }

    /** Clear all session data (used on sign-out / role switch) */
    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
