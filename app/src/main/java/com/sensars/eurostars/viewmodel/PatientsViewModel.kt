package com.sensars.eurostars.viewmodel

import androidx.lifecycle.ViewModel
import com.sensars.eurostars.data.PatientsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PatientsViewModel(
    private val repo: PatientsRepository = PatientsRepository()
) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun createPatient(
        patientId: String,
        weightInput: String,
        ageInput: String,
        heightInput: String,
        onSuccess: () -> Unit
    ) {
        _error.value = null

        val pid = patientId.trim()
        val weight = weightInput.trim().toIntOrNull()
        val age = ageInput.trim().toIntOrNull()
        val height = heightInput.trim().toIntOrNull()

        if (pid.isEmpty()) { _error.value = "Patient ID is required."; return }
        if (weight == null || weight !in 20..300) { _error.value = "Enter a valid weight (kg)."; return }
        if (age == null || age !in 1..120) { _error.value = "Enter a valid age."; return }
        if (height == null || height !in 50..250) { _error.value = "Enter a valid height (cm)."; return }

        _loading.value = true
        repo.createPatient(
            patientId = pid,
            weightKg = weight,
            ageYears = age,
            heightCm = height,
            onSuccess = {
                _loading.value = false
                onSuccess()
            },
            onError = { msg ->
                _loading.value = false
                _error.value = msg
            }
        )
    }
}
