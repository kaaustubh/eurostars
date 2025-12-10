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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sensars.eurostars.data.model.Patient
import com.sensars.eurostars.ui.design.LightGreen100

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

@Composable
fun PatientCard(
    patient: Patient,
    modifier: Modifier = Modifier,
    onEditClick: (() -> Unit)? = null
) {
    // Colors from the design
    val labelColor = Color(0xFF62828C) // Muted blue-grey for labels
    val valueColor = Color(0xFF2C7B8C) // Darker teal-blue for values
    
    Box(
        modifier = modifier
            .background(
                color = LightGreen100, // Light green 100 background
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Patient Clinical Study ID#
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = labelColor, fontSize = 14.sp, fontWeight = FontWeight.Normal)) {
                        append("Patient Clinical Study ID#: ")
                    }
                    withStyle(SpanStyle(color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)) {
                        append(patient.patientId)
                    }
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 32.dp) // Space for edit button
            )

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
        }

        if (onEditClick != null) {
            IconButton(
                onClick = onEditClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = labelColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

