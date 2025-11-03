package com.sensars.eurostars.ui.screens.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sensars.eurostars.utils.AppVersionInfo
import com.sensars.eurostars.utils.getAppVersionInfo
import com.sensars.eurostars.utils.readReleaseNotes
import kotlinx.coroutines.launch

@Composable
fun AboutSection(
    showReleaseNotes: Boolean = false,
    onShowReleaseNotesChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var versionInfo by remember { mutableStateOf<AppVersionInfo?>(null) }
    var releaseNotes by remember { mutableStateOf<String?>(null) }
    var isLoadingNotes by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        versionInfo = getAppVersionInfo(context)
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (versionInfo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Version",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = versionInfo!!.versionName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Build",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = versionInfo!!.versionCode.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    if (releaseNotes == null) {
                        isLoadingNotes = true
                        scope.launch {
                            releaseNotes = com.sensars.eurostars.utils.readReleaseNotes(context)
                            isLoadingNotes = false
                            onShowReleaseNotesChange(true)
                        }
                    } else {
                        onShowReleaseNotesChange(true)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoadingNotes
            ) {
                if (isLoadingNotes) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("View Release Notes")
            }
        }
    }
    
    // Release Notes Dialog
    if (showReleaseNotes && releaseNotes != null) {
        val scrollState = rememberScrollState()
        
        AlertDialog(
            onDismissRequest = { onShowReleaseNotesChange(false) },
            title = {
                Text(
                    text = "Release Notes",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .verticalScroll(scrollState)
                ) {
                    com.sensars.eurostars.ui.components.MarkdownText(
                        markdown = releaseNotes!!,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onShowReleaseNotesChange(false) }) {
                    Text("Close")
                }
            }
        )
    }
}

