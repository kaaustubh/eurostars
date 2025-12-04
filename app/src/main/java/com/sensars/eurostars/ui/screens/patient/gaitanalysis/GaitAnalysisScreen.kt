package com.sensars.eurostars.ui.screens.patient.gaitanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sensars.eurostars.data.UploadStatus
import com.sensars.eurostars.data.WalkSession
import com.sensars.eurostars.viewmodel.gaitAnalysisViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GaitAnalysisScreen() {
    val viewModel = gaitAnalysisViewModel()
    val sessions by viewModel.sessions.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadSessions()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Gait Analysis History",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (error != null) {
            Text(
                text = error ?: "",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (loading && sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            SessionTable(
                sessions = sessions,
                onRetry = { viewModel.retryUpload(it) }
            )
        }
    }
}

@Composable
fun SessionTable(
    sessions: List<WalkSession>,
    onRetry: (String) -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Start/End Time", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("ID", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Size", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Status", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            
            Divider()

            LazyColumn {
                items(sessions) { session ->
                    SessionRow(session, onRetry)
                    Divider()
                }
            }
            
            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No sessions recorded yet")
                }
            }
        }
    }
}

@Composable
fun SessionRow(
    session: WalkSession,
    onRetry: (String) -> Unit
) {
    val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    val startStr = dateFormat.format(Date(session.startTime))
    val endStr = dateFormat.format(Date(session.endTime))
    
    val sizeKb = session.dataSizeBytes / 1024
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(2f)) {
            Text(startStr, fontSize = 12.sp)
            Text(endStr, fontSize = 12.sp, color = Color.Gray)
        }
        
        Text(
            text = session.displaySessionId,
            modifier = Modifier.weight(0.8f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Text(
            text = "${sizeKb} KB",
            modifier = Modifier.weight(0.8f),
            fontSize = 12.sp
        )
        
        Box(modifier = Modifier.weight(1.2f)) {
            if (session.status == UploadStatus.FAILED) {
                Button(
                    onClick = { onRetry(session.sessionId) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Retry", fontSize = 10.sp)
                }
            } else {
                Text(
                    text = session.status.name,
                    fontSize = 11.sp,
                    color = when(session.status) {
                        UploadStatus.UPLOADED -> Color(0xFF4CAF50) // Green
                        UploadStatus.PENDING, UploadStatus.UPLOADING -> Color(0xFFFF9800) // Orange
                        else -> Color.Black
                    },
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
