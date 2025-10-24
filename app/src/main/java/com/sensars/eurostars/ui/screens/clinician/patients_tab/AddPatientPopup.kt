package com.sensars.eurostars.ui.screens.clinician.patients_tab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sensars.eurostars.ui.design.Aquamarine

@Composable
fun AddPatientPopup(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onCreatePatient: (PatientData) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    if (isVisible) {
        var patientId by remember { mutableStateOf("") }
        var weight by remember { mutableStateOf("") }
        var age by remember { mutableStateOf("") }
        var height by remember { mutableStateOf("") }
        
        // Validation: check if all fields are filled and numeric
        val isPatientIdNumeric = patientId.isNotBlank() && patientId.all { it.isDigit() }
        val isWeightNumeric = weight.isNotBlank() && weight.all { it.isDigit() }
        val isAgeNumeric = age.isNotBlank() && age.all { it.isDigit() }
        val isHeightNumeric = height.isNotBlank() && height.all { it.isDigit() }
        
        val isFormValid = isPatientIdNumeric && 
                         isWeightNumeric && 
                         isAgeNumeric && 
                         isHeightNumeric

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Card(
                modifier = Modifier
                    .width(600.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        // Header with title and close button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Title only (removed the "G" icon)
                            Text(
                                text = "Create a patient",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            )

                            // Close icon button at top-right
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close",
                                    tint = Color.Gray
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // Form fields
                        PatientFormField(
                            label = "Patient Clinical Study ID# *",
                            value = patientId,
                            onValueChange = { patientId = it },
                            placeholder = "Enter patient ID",
                            isError = patientId.isNotBlank() && !patientId.all { it.isDigit() }
                        )
                        
                        // Show error message for Patient ID if not numeric
                        if (patientId.isNotBlank() && !patientId.all { it.isDigit() }) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Patient ID must contain only numbers",
                                color = Color.Red,
                                fontSize = 12.sp,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        // Weight field
                        PatientFormField(
                            label = "Weight *",
                            value = weight,
                            onValueChange = { weight = it },
                            placeholder = "Enter your weight",
                            unit = "kg",
                            isError = weight.isNotBlank() && !weight.all { it.isDigit() }
                        )
                        
                        // Show error message for Weight if not numeric
                        if (weight.isNotBlank() && !weight.all { it.isDigit() }) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Weight must contain only numbers",
                                color = Color.Red,
                                fontSize = 12.sp,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Age field
                        PatientFormField(
                            label = "Age *",
                            value = age,
                            onValueChange = { age = it },
                            placeholder = "Enter your age",
                            isError = age.isNotBlank() && !age.all { it.isDigit() }
                        )
                        
                        // Show error message for Age if not numeric
                        if (age.isNotBlank() && !age.all { it.isDigit() }) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Age must contain only numbers",
                                color = Color.Red,
                                fontSize = 12.sp,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Height field
                        PatientFormField(
                            label = "Height *",
                            value = height,
                            onValueChange = { height = it },
                            placeholder = "Enter your height",
                            unit = "cm",
                            isError = height.isNotBlank() && !height.all { it.isDigit() }
                        )
                        
                        // Show error message for Height if not numeric
                        if (height.isNotBlank() && !height.all { it.isDigit() }) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Height must contain only numbers",
                                color = Color.Red,
                                fontSize = 12.sp,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(Modifier.height(32.dp))

                        // Error message display
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage,
                                color = Color.Red,
                                fontSize = 14.sp,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(16.dp))
                        }

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Cancel button
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Aquamarine
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    Aquamarine
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Cancel",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }

                            // Create patient button
                            Button(
                                onClick = {
                                    if (isFormValid && !isLoading) {
                                        onCreatePatient(
                                            PatientData(
                                                id = patientId,
                                                weight = weight,
                                                age = age,
                                                height = height
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = isFormValid && !isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFormValid && !isLoading) Aquamarine else Color.Gray,
                                    disabledContainerColor = Color.Gray
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isLoading) {
                                    Text(
                                        text = "Creating...",
                                        color = Color.LightGray,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                } else {
                                    Text(
                                        text = "Create a patient",
                                        color = if (isFormValid) Color.White else Color.LightGray,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun PatientFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            ),
            color = Color(0xFF2C2C2C)
        )
        
        Spacer(Modifier.height(8.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp,
                    color = Color(0xFF2C2C2C)
                ),
                cursorBrush = SolidColor(Aquamarine),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (label.contains("Age") || label.contains("Weight") || label.contains("Height") || label.contains("Patient Clinical Study ID"))
                        KeyboardType.Number
                    else 
                        KeyboardType.Text
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF8F9FA))
                            .border(
                                1.dp,
                                if (isError) Color.Red else Color(0xFFE1E5E9),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = Color(0xFF9AA4A7),
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
            
            if (unit != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFF0F2F3))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = unit,
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

data class PatientData(
    val id: String,
    val weight: String,
    val age: String,
    val height: String
)
