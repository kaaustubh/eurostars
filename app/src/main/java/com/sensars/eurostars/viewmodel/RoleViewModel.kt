package com.sensars.eurostars.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import com.sensars.eurostars.data.RoleRepository
import com.sensars.eurostars.data.UserRole

class RoleViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RoleRepository(app)

    fun readRole(): Flow<UserRole?> = repo.roleFlow

    fun setPatient() = viewModelScope.launch { repo.setRole(UserRole.PATIENT) }

    fun setClinician() = viewModelScope.launch { repo.setRole(UserRole.CLINICIAN) }

    fun clearRole() = viewModelScope.launch { repo.clearRole() }
}
