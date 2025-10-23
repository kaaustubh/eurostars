import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.sensars.eurostars.ui.screens.clinician.patients_tab.PatientsHeader

@Composable
fun PatientsScreen(
    onAddPatient: () -> Unit
) {
    val (query, setQuery) = remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        PatientsHeader(
            query = query,
            onQueryChange = setQuery,
            onAddPatient = onAddPatient
        )
        // TODO: Grid/list of patient cards filtered by `query`
    }
}
