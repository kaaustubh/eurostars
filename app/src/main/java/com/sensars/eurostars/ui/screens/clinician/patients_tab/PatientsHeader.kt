package com.sensars.eurostars.ui.screens.clinician.patients_tab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.google.firebase.database.collection.LLRBNode
import com.sensars.eurostars.ui.utils.rememberWindowWidthClass

/**
 * Patients list header:
 *  - Title ("Patients")
 *  - Search box
 *  - Add New Patient button
 */

@Composable
fun PatientsHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onAddPatient: () -> Unit,
    modifier: Modifier = Modifier
) {
    val width = rememberWindowWidthClass()
    val isCompact = width == WindowWidthSizeClass.Compact

    val sidebarColor = Color(0xFF466871)
    val searchBg = Color(0xFFF5F7F7)
    val searchBorder = Color(0xFFCBD5D7)

    if (isCompact) {
        // Compact (phone)
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Patients",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
            )
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                SearchField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                AddPatientButton(onClick = onAddPatient, color = sidebarColor)
            }
        }
    } else {
        // Tablet / desktop
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Patients",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp
                )
            )
            Spacer(Modifier.weight(1f))
            SearchField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.width(280.dp)
            )
            Spacer(Modifier.width(16.dp))
            AddPatientButton(onClick = onAddPatient, color = sidebarColor)
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchBg = Color(0xFFF5F7F7)
    val searchBorder = Color(0xFFCBD5D7)
    val corner = RoundedCornerShape(10.dp)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(Color(0xFF466871)),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = Color(0xFF4A4A4A),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text("Search", color = Color(0xFF9AA4A7), fontSize = 16.sp)
                    }
                    innerTextField()
                }
            }
        },
        modifier = modifier
            .height(42.dp)
            .clip(corner)
            .background(searchBg, corner)
            .border(1.dp, searchBorder, corner)
            .padding(horizontal = 12.dp)
    )
}


@Composable
private fun AddPatientButton(onClick: () -> Unit, color: Color) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.height(42.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = "Add",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "Add New Patient",
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp
        )
    }
}
