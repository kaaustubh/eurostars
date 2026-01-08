package com.sensoria.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import com.sensoria.app.data.RoleRepository
import com.sensoria.app.data.UserRole

class RoleViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RoleRepository(app)

    fun readRole(): Flow<UserRole?> = repo.roleFlow

    fun setPatient() = viewModelScope.launch { repo.setRole(UserRole.PATIENT) }

    fun setClinician() = viewModelScope.launch { repo.setRole(UserRole.CLINICIAN) }

    fun clearRole() = viewModelScope.launch { repo.clearRole() }
}
