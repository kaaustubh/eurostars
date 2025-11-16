import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.sensars.eurostars.ui.screens.clinician.patients_tab.PatientsHeader
import com.sensars.eurostars.ui.screens.clinician.patients_tab.AddPatientPopup
import com.sensars.eurostars.ui.screens.clinician.patients_tab.PatientCard
import com.sensars.eurostars.ui.screens.clinician.patients_tab.PatientData
import com.sensars.eurostars.ui.utils.rememberWindowWidthClass
import com.sensars.eurostars.viewmodel.PatientsViewModel

@Composable
fun PatientsScreen(
    onAddPatient: () -> Unit
) {
    val (query, setQuery) = remember { mutableStateOf("") }
    val (showAddPatientPopup, setShowAddPatientPopup) = remember { mutableStateOf(false) }
    var editPatientData by remember { mutableStateOf<PatientData?>(null) }
    
    // Initialize ViewModel
    val viewModel: PatientsViewModel = viewModel()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val patients by viewModel.patients.collectAsState()
    val nextPatientId by viewModel.nextPatientId.collectAsState()
    val editPatientDataState by viewModel.editPatientData.collectAsState()

    // Get window size for responsive layout
    val width = rememberWindowWidthClass()
    val isCompact = width == WindowWidthSizeClass.Compact
    
    // Filter patients based on search query
    val filteredPatients = remember(patients, query) {
        if (query.isBlank()) {
            patients
        } else {
            patients.filter { patient ->
                patient.patientId.contains(query, ignoreCase = true)
            }
        }
    }

    // Determine number of columns based on screen size
    val gridColumns = if (isCompact) {
        GridCells.Fixed(1) // 1 column on mobile
    } else {
        GridCells.Fixed(3) // 3 columns on tablet/desktop
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        PatientsHeader(
            query = query,
            onQueryChange = setQuery,
            onAddPatient = { 
                viewModel.clearError()
                viewModel.loadPatients()
                setShowAddPatientPopup(true) 
            }
        )
        
        // Patient list grid
        if (loading && patients.isEmpty()) {
            // Show loading indicator only on initial load
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (filteredPatients.isEmpty()) {
            // Show empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (query.isBlank()) "No patients found" else "No patients match your search",
                    fontSize = 16.sp,
                    color = Color.Gray
                )
            }
        } else {
            LazyVerticalGrid(
                columns = gridColumns,
                contentPadding = PaddingValues(
                    horizontal = if (isCompact) 20.dp else 32.dp,
                    vertical = 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredPatients) { patient ->
                    PatientCard(
                        patient = patient,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Fetch full details for edit and open dialog
                                viewModel.loadPatientForEdit(patient.patientId)
                                setShowAddPatientPopup(true)
                            }
                    )
                }
            }
        }
    }

    // Add Patient Popup
    AddPatientPopup(
        isVisible = showAddPatientPopup,
        onDismiss = { 
            setShowAddPatientPopup(false)
            viewModel.clearEditPatient()
        },
        onCreatePatient = { patientData ->
            viewModel.createPatient(
                patientId = patientData.id,
                weightInput = patientData.weight,
                ageInput = patientData.age,
                heightInput = patientData.height,
                neuropathicLeg = patientData.neuropathicLeg,
                dateOfLastUlcer = patientData.dateOfLastUlcer,
                ulcerActive = patientData.ulcerActive,
                onSuccess = {
                    setShowAddPatientPopup(false)
                    viewModel.clearEditPatient()
                    // Patient list will be refreshed automatically via loadPatients() in ViewModel
                }
            )
        },
        initialData = editPatientDataState ?: editPatientData,
        isLoading = loading,
        errorMessage = error,
        autoGeneratedPatientId = if ((editPatientDataState ?: editPatientData) == null) nextPatientId else (editPatientDataState
            ?: editPatientData)?.id
    )
}
