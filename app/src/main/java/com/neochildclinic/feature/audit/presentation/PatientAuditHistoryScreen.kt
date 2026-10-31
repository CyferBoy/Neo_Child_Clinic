package com.neochildclinic.feature.audit.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.designsystem.Spacing
import com.neochildclinic.core.ui.*
import com.neochildclinic.domain.model.AuditLog
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.feature.audit.domain.repository.AuditLogRepository
import com.neochildclinic.feature.patient.domain.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PatientAuditViewModel @Inject constructor(
    private val auditLogRepository: AuditLogRepository,
    private val patientRepository: PatientRepository
) : ViewModel() {
    val auditLogPager = PatientAuditLogPager(auditLogRepository, viewModelScope)

    fun getPatient(patientId: String): Flow<Patient?> =
        patientRepository.allPatients.map { list -> list.find { it.id == patientId } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientAuditHistoryScreen(
    patientId: String,
    onBack: () -> Unit,
    viewModel: PatientAuditViewModel = hiltViewModel()
) {
    val auditState by viewModel.auditLogPager.state.collectAsState()
    val patient by viewModel.getPatient(patientId).collectAsState(initial = null)

    LaunchedEffect(patientId) {
        viewModel.auditLogPager.load(patientId)
    }

    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    val filterOptions = listOf("All", "Patient", "Vaccination", "Consultation")

    val filteredLogs = remember(auditState.logs, selectedFilter) {
        if (selectedFilter == "All") {
            auditState.logs
        } else {
            auditState.logs.filter { log ->
                val mod = log.module.uppercase()
                val ent = log.entityType.uppercase()
                when (selectedFilter) {
                    "Patient" -> mod.contains("PATIENT") || ent.contains("PATIENT")
                    "Vaccination" -> mod.contains("VACCIN") || ent.contains("VACCIN")
                    "Consultation" -> mod.contains("CONSULT") || ent.contains("CONSULT")
                    else -> true
                }
            }
        }
    }

    val groupedLogs = remember(filteredLogs) { groupPatientLogsByDate(filteredLogs) }

    AppBackground {
        Scaffold(
            topBar = {
                BackTopAppBar(
                    title = {
                        Column {
                            Text("Audit History")
                            if (patient != null) {
                                Text(
                                    text = "${patient?.name} · ${patient?.patientClinicId ?: patient?.gender.orEmpty()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onBack = onBack
                )
            }
        ) { paddingValues ->
            AppPullToRefresh(
                isRefreshing = auditState.isLoading && auditState.logs.isNotEmpty(),
                onRefresh = { viewModel.auditLogPager.load(patientId) },
                modifier = Modifier.padding(paddingValues)
            ) {
                if (auditState.isLoading && auditState.logs.isEmpty()) {
                    SkeletonList(
                        modifier = Modifier.fillMaxSize(),
                        count = 8,
                        cardShaped = true,
                        spacing = Spacing.sm,
                        contentPadding = PaddingValues(Spacing.lg)
                    )
                } else if (auditState.error != null && auditState.logs.isEmpty()) {
                    ErrorState(
                        message = auditState.error.orEmpty(),
                        onRetry = { viewModel.auditLogPager.load(patientId) }
                    )
                } else {
                    val listState = rememberLazyListState()

                    LaunchedEffect(listState, auditState.logs.size, auditState.hasMore) {
                        snapshotFlow {
                            val layout = listState.layoutInfo
                            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                            lastVisible >= auditState.logs.size - 5
                        }.collect { shouldLoadMore ->
                            if (shouldLoadMore) viewModel.auditLogPager.loadMore()
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp, top = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        // 1. Filter Chips Row
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.screen)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                filterOptions.forEach { filter ->
                                    val isSelected = selectedFilter == filter
                                    Surface(
                                        onClick = { selectedFilter = filter },
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        ),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        ) {
                                            Text(
                                                text = filter,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Section Eyebrow Row
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.screen, vertical = Spacing.xxs),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PATIENT AUDIT EVENTS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${filteredLogs.size} events",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 3. Grouped Audit Items
                        if (filteredLogs.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                    title = "No audit events found",
                                    message = "No audit history recorded for this filter."
                                )
                            }
                        } else {
                            groupedLogs.forEach { (dateHeader, logsForDate) ->
                                item(key = "header_$dateHeader") {
                                    Text(
                                        text = dateHeader,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .padding(horizontal = Spacing.screen)
                                            .padding(top = 10.dp, bottom = 4.dp)
                                    )
                                }
                                items(logsForDate, key = { it.id }) { log ->
                                    Box(modifier = Modifier.padding(horizontal = Spacing.screen)) {
                                        AuditLogItem(log)
                                    }
                                }
                            }
                        }

                        if (auditState.isLoadingMore) {
                            item(key = "patient_audit_loading_more") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }

                        if (auditState.error != null) {
                            item(key = "patient_audit_load_more_error") {
                                ErrorState(
                                    message = "Couldn't load more: ${auditState.error}",
                                    onRetry = { viewModel.auditLogPager.loadMore() },
                                    retryLabel = "Retry",
                                    modifier = Modifier.padding(vertical = Spacing.sm)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun groupPatientLogsByDate(logs: List<AuditLog>): Map<String, List<AuditLog>> {
    val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.US)
    val map = linkedMapOf<String, MutableList<AuditLog>>()

    for (log in logs) {
        val dateStr = runCatching {
            val instant = Instant.parse(log.timestamp)
            val localDate = instant.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate()
            localDate.format(dateFormatter)
        }.getOrElse {
            if (log.timestamp.length >= 10) {
                runCatching {
                    val localDate = LocalDate.parse(log.timestamp.substring(0, 10))
                    localDate.format(dateFormatter)
                }.getOrDefault("Earlier")
            } else {
                "Recent"
            }
        }

        map.getOrPut(dateStr) { mutableListOf() }.add(log)
    }

    return map
}
