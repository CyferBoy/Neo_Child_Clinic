package com.neochildclinic.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.SearchTopAppBar
import com.neochildclinic.domain.model.Patient

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onPatientClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    val customColors = LocalCustomColors.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = customColors.bgOffWhite
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                SearchTopAppBar(
                    title = "Search Patients",
                    searchQuery = query,
                    onSearchQueryChange = viewModel::onQueryChange,
                    isSearchActive = true,
                    onSearchActiveChange = { if (!it) onBack() },
                    onBack = onBack,
                    placeholder = "Name, Phone, ID or Vaccine..."
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.results.isEmpty() && query.isNotBlank()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No results found for \"$query\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.results, key = { it.id }) { patient ->
                            SearchResultItem(
                                patient = patient,
                                onClick = { onPatientClick(patient.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    patient: Patient,
    onClick: () -> Unit
) {
    val customColors = LocalCustomColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color.Black.copy(alpha = 0.05f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(customColors.softBlue)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = patient.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = customColors.textBlue
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val clinicIdDisplay = if (patient.patientClinicId.isNullOrBlank() || patient.patientClinicId.startsWith("TEMP-")) "Not Assigned" else patient.patientClinicId
                Text(
                    text = "ID: $clinicIdDisplay",
                    style = MaterialTheme.typography.bodySmall,
                    color = customColors.textBlue.copy(alpha = 0.7f)
                )
                Text(
                    text = patient.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = customColors.textBlue.copy(alpha = 0.7f)
                )
            }
        }
    }
}
