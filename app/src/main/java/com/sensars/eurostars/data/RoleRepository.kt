package com.sensars.eurostars.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.roleDataStore by preferencesDataStore("sensole_prefs")
private val KEY_ROLE = stringPreferencesKey("role")

enum class UserRole { PATIENT, CLINICIAN }

class RoleRepository(private val context: Context) {
    val roleFlow = context.roleDataStore.data.map { prefs ->
        when (prefs[KEY_ROLE]) {
            "clinician" -> UserRole.CLINICIAN
            "patient" -> UserRole.PATIENT
            else -> null
        }
    }

    suspend fun setRole(role: UserRole) {
        context.roleDataStore.edit { it[KEY_ROLE] = when (role) {
            UserRole.PATIENT -> "patient"
            UserRole.CLINICIAN -> "clinician"
        } }
    }

    suspend fun clearRole() {
        context.roleDataStore.edit { it.remove(KEY_ROLE) }
    }
}
