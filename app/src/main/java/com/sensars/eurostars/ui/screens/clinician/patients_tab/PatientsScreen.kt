import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sensars.eurostars.ui.screens.clinician.patients_tab.PatientsHeader
import com.sensars.eurostars.ui.screens.clinician.patients_tab.AddPatientPopup
import com.sensars.eurostars.viewmodel.PatientsViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun PatientsScreen(
    onAddPatient: () -> Unit
) {
    val (query, setQuery) = remember { mutableStateOf("") }
    val (showAddPatientPopup, setShowAddPatientPopup) = remember { mutableStateOf(false) }
    
    // Initialize ViewModel
    val viewModel: PatientsViewModel = viewModel()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    Column(Modifier.fillMaxSize()) {
        PatientsHeader(
            query = query,
            onQueryChange = setQuery,
            onAddPatient = { 
                viewModel.clearError()
                setShowAddPatientPopup(true) 
            }
        )
    }

    // Add Patient Popup
    AddPatientPopup(
        isVisible = showAddPatientPopup,
        onDismiss = { setShowAddPatientPopup(false) },
        onCreatePatient = { patientData ->
            viewModel.createPatient(
                patientId = patientData.id,
                weightInput = patientData.weight,
                ageInput = patientData.age,
                heightInput = patientData.height,
                onSuccess = {
                    setShowAddPatientPopup(false)
                    // TODO: Refresh patient list or show success message
                }
            )
        },
        isLoading = loading,
        errorMessage = error
    )
}
