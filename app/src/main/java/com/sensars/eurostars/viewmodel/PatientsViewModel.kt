package com.sensars.eurostars.viewmodel

import androidx.lifecycle.ViewModel
import com.sensars.eurostars.data.PatientsRepository
import com.sensars.eurostars.data.model.Patient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PatientsViewModel(
    private val repo: PatientsRepository = PatientsRepository()
) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _patients = MutableStateFlow<List<Patient>>(emptyList())
    val patients = _patients.asStateFlow()

    init {
        loadPatients()
    }

    fun clearError() {
        _error.value = null
    }

    fun loadPatients() {
        _loading.value = true
        _error.value = null
        repo.getPatients(
            onSuccess = { patients ->
                _patients.value = patients
                _loading.value = false
            },
            onError = { msg ->
                _error.value = msg
                _loading.value = false
            }
        )
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
                loadPatients() // Refresh the patient list after creating a new patient
            },
            onError = { msg ->
                _loading.value = false
                _error.value = msg
            }
        )
    }
}
