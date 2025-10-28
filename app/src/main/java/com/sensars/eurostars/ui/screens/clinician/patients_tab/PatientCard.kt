package com.sensars.eurostars.ui.screens.clinician.patients_tab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sensars.eurostars.data.model.Patient
import com.sensars.eurostars.ui.design.LightGreen100

@Composable
fun PatientCard(
    patient: Patient,
    modifier: Modifier = Modifier
) {
    // Colors from the design
    val labelColor = Color(0xFF62828C) // Muted blue-grey for labels
    val valueColor = Color(0xFF2C7B8C) // Darker teal-blue for values
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = LightGreen100, // Light green 100 background
                shape = RoundedCornerShape(8.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Patient Clinical Study ID#
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Patient Clinical Study ID#: ",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = labelColor
            )
            Text(
                text = patient.patientId,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }

        // Age
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Age: ",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = labelColor
            )
            Text(
                text = "${patient.age} y.o.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }

        // Origin of pain
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Origin of pain: ",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = labelColor,
                modifier = Modifier.wrapContentWidth(Alignment.Start)
            )
            Text(
                text = patient.originOfPain.ifEmpty { "N/A" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                modifier = Modifier.weight(1f),
                softWrap = true
            )
        }
    }
}

