package com.neochildclinic.features.inventory

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.data.local.entity.VisitEntity
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.utils.PatientUtils.formatDateForDisplay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryIssuesScreen(
    onBack: () -> Unit,
    viewModel: InventoryIssuesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    AppBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Inventory Issues") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { paddingValues ->
            if (uiState.pendingVisits.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text("No pending inventory issues found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.pendingVisits, key = { it.id }) { visit ->
                        InventoryIssueCard(
                            visit = visit,
                            onRetry = { viewModel.retryDeduction(visit) },
                            onResolveManual = { batchId -> viewModel.resolveManual(visit, batchId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InventoryIssueCard(
    visit: VisitEntity,
    onRetry: () -> Unit,
    onResolveManual: (String) -> Unit
) {
    var showResolveDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Unresolved Stock Deduction", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                Badge(containerColor = MaterialTheme.colorScheme.error) {
                    Text(visit.inventoryStatus, color = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(text = "Patient: ${visit.patientId}", style = MaterialTheme.typography.bodySmall) // ideally fetch name but Id is safe for now
            Text(text = "Vaccines: ${visit.vaccineNames}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text = "Date: ${formatDateForDisplay(visit.dateGiven)}", style = MaterialTheme.typography.bodySmall)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Retry FEFO")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { showResolveDialog = true }) {
                    Text("Resolve Manually")
                }
            }
        }
    }
    
    // Resolve dialog could be added here to pick a batch
}
