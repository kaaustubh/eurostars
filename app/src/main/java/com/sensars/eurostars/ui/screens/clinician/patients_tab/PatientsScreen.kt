import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.sensars.eurostars.ui.screens.clinician.patients_tab.PatientsHeader
import com.sensars.eurostars.ui.screens.clinician.patients_tab.AddPatientPopup
import com.sensars.eurostars.ui.screens.clinician.patients_tab.PatientData

@Composable
fun PatientsScreen(
    onAddPatient: () -> Unit
) {
    val (query, setQuery) = remember { mutableStateOf("") }
    val (showAddPatientPopup, setShowAddPatientPopup) = remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        PatientsHeader(
            query = query,
            onQueryChange = setQuery,
            onAddPatient = { setShowAddPatientPopup(true) }
        )
        // TODO: Grid/list of patient cards filtered by `query`
    }

    // Add Patient Popup
    AddPatientPopup(
        isVisible = showAddPatientPopup,
        onDismiss = { setShowAddPatientPopup(false) },
        onCreatePatient = { patientData ->
            // Handle patient creation
            println("Creating patient: $patientData")
            setShowAddPatientPopup(false)
        }
    )
}
