package com.sensoria.app.data.model

data class Patient(
    val patientId: String,
    val age: Int,
    val originOfPain: String = "" // e.g., "Right foot, right calf"
)

