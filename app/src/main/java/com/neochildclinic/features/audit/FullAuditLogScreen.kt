package com.neochildclinic.features.audit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.data.local.entity.AuditLogEntity
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullAuditLogScreen(
    onBack: () -> Unit,
    viewModel: FullAuditLogViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    AppBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Application Audit") },
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
            AppPullToRefresh(
                isRefreshing = uiState.isLoading,
                onRefresh = viewModel::refresh,
                modifier = Modifier.padding(paddingValues)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    AuditPeriodSelector(
                        start = uiState.periodStart,
                        end = uiState.periodEnd,
                        canGoNext = uiState.periodEnd.isBefore(java.time.LocalDate.now()),
                        onPrevious = viewModel::previousPeriod,
                        onNext = viewModel::nextPeriod,
                        onCurrent = viewModel::currentPeriod
                    )
                    AuditFilters(
                        actions = uiState.availableActions,
                        users = uiState.availableUsers,
                        selectedAction = uiState.selectedAction,
                        selectedUser = uiState.selectedUser,
                        onActionSelected = viewModel::setAction,
                        onUserSelected = viewModel::setUser
                    )

                    when {
                        uiState.isLoading && uiState.logs.isEmpty() -> Box(
                            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                        uiState.error != null -> Box(
                            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                        ) { Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error) }
                        uiState.logs.isEmpty() -> Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center
                        ) { Text("No audit logs found for this 7-day period", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.logs, key = { it.id }) { log -> AuditLogItem(log) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditPeriodSelector(
    start: java.time.LocalDate,
    end: java.time.LocalDate,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrevious) { Icon(Icons.Default.ChevronLeft, "Previous 7 days") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${start.format(formatter)} – ${end.format(formatter)}", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onCurrent) { Text("Current 7 days") }
            }
            IconButton(onClick = onNext, enabled = canGoNext) { Icon(Icons.Default.ChevronRight, "Next 7 days") }
        }
    }
}

@Composable
private fun AuditFilters(
    actions: List<String>,
    users: List<String>,
    selectedAction: String,
    selectedUser: String,
    onActionSelected: (String) -> Unit,
    onUserSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AuditDropdown(
            modifier = Modifier.weight(1f),
            label = "Action",
            selected = selectedAction,
            options = listOf("ALL") + actions,
            onSelected = onActionSelected
        )
        AuditDropdown(
            modifier = Modifier.weight(1f),
            label = "User",
            selected = selectedUser,
            options = listOf("ALL") + users,
            onSelected = onUserSelected
        )
    }
}

@Composable
private fun AuditDropdown(
    modifier: Modifier,
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selected == "ALL") "All $label" else selected, maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(if (option == "ALL") "All $label" else option) },
                    onClick = { onSelected(option); expanded = false }
                )
            }
        }
    }
}

@Composable
fun AuditLogItem(log: AuditLogEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = getModuleColor(log.module),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = log.module,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
                Text(
                    text = PatientUtils.formatDate(PatientUtils.parseDate(log.timestamp) ?: Date(0)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "${log.action}: ${log.entityType}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
            
            if (log.remarks != null) {
                Text(
                    text = log.remarks!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "User: ${log.user}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            if (log.device != null) {
                Text(
                    text = "Device: ${log.device}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

fun getModuleColor(module: String): Color = when (module.uppercase()) {
    "PATIENT" -> Color(0xFF2196F3)
    "VACCINE", "INVENTORY" -> Color(0xFFFF9800)
    "FINANCE" -> Color(0xFF4CAF50)
    "STAFF", "USERS" -> Color(0xFF9C27B0)
    "SYSTEM", "SYNC" -> Color(0xFF607D8B)
    else -> Color(0xFF757575)
}
