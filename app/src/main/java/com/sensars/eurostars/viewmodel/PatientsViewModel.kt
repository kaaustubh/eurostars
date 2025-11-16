package com.sensars.eurostars.viewmodel

import androidx.lifecycle.ViewModel
import com.sensars.eurostars.data.PatientsRepository
import com.sensars.eurostars.data.model.Patient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.sensars.eurostars.ui.screens.clinician.patients_tab.PatientData

class PatientsViewModel(
    private val repo: PatientsRepository = PatientsRepository()
) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _patients = MutableStateFlow<List<Patient>>(emptyList())
    val patients = _patients.asStateFlow()

    private val _nextPatientId = MutableStateFlow<String?>(null)
    val nextPatientId = _nextPatientId.asStateFlow()

    // For edit mode prefill
    private val _editPatientData = MutableStateFlow<PatientData?>(null)
    val editPatientData = _editPatientData.asStateFlow()

    init {
        loadPatients()
    }

    fun clearError() {
        _error.value = null
    }

    fun clearEditPatient() {
        _editPatientData.value = null
    }

    fun loadPatients() {
        _loading.value = true
        _error.value = null
        _nextPatientId.value = null
        repo.getPatients(
            onSuccess = { patients ->
                _patients.value = patients
                _nextPatientId.value = calculateNextPatientId(patients)
                _loading.value = false
            },
            onError = { msg ->
                _error.value = msg
                _nextPatientId.value = null
                _loading.value = false
            }
        )
    }

    fun loadPatientForEdit(patientId: String) {
        _loading.value = true
        _error.value = null
        repo.getPatientById(
            patientId = patientId,
            onSuccess = { full ->
                _loading.value = false
                _editPatientData.value = PatientData(
                    id = full.patientId,
                    weight = full.weightKg?.toString() ?: "",
                    age = full.ageYears?.toString() ?: "",
                    height = full.heightCm?.toString() ?: "",
                    neuropathicLeg = full.neuropathicLeg ?: "",
                    dateOfLastUlcer = full.dateOfLastUlcer ?: "",
                    ulcerActive = full.ulcerActive ?: ""
                )
            },
            onError = { msg ->
                _loading.value = false
                _error.value = msg
            }
        )
    }

    fun createPatient(
        patientId: String,
        weightInput: String,
        ageInput: String,
        heightInput: String,
        neuropathicLeg: String = "",
        dateOfLastUlcer: String = "",
        ulcerActive: String = "",
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
            neuropathicLeg = neuropathicLeg.trim(),
            dateOfLastUlcer = dateOfLastUlcer.trim(),
            ulcerActive = ulcerActive.trim(),
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

    private fun calculateNextPatientId(patients: List<Patient>): String? {
        val numericIds = patients.mapNotNull { patient ->
            val raw = patient.patientId.trim()
            raw.toLongOrNull()
                ?: raw.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toLongOrNull()
        }

        val nextNumeric = (numericIds.maxOrNull() ?: 0L) + 1L

        if (patients.isEmpty()) {
            return nextNumeric.toString().padStart(4, '0')
        }

        val hasLeadingZeros = patients.any { patient ->
            val trimmed = patient.patientId.trim()
            trimmed.length > 1 && trimmed.startsWith('0')
        }

        return if (hasLeadingZeros) {
            val width = maxOf(patients.maxOfOrNull { it.patientId.length } ?: 4, 4)
            nextNumeric.toString().padStart(width, '0')
        } else {
            nextNumeric.toString()
        }
    }
}
