package com.neochildclinic.features.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.SkeletonList
import com.neochildclinic.core.utils.PatientUtils
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestonePatientsScreen(
    milestoneKey: String,
    onBack: () -> Unit,
    onPatientClick: (String) -> Unit,
    viewModel: MilestonePatientsViewModel = hiltViewModel()
) {
    val patients by viewModel.patients.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val label = remember(milestoneKey) { StatisticsUtils.milestoneLabelForKey(milestoneKey) ?: "" }

    val today = remember { Calendar.getInstance() }
    val windowEnd = remember { Calendar.getInstance().apply { add(Calendar.MONTH, 2) } }

    // Same one-patient-one-milestone rule as the summary cards (PatientsTab), so the list
    // shown here always matches the count on the card that opened it. "Older" has no
    // milestone date from getNextAgeMilestone() (it only looks ahead) - those patients are
    // identified the same way the summary card counts them, and sorted oldest DOB first as
    // the natural analog of "earliest milestone date first".
    val entries = remember(patients, label, milestoneKey) {
        val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
        if (milestoneKey == "older") {
            patients
                .filter { PatientUtils.getNextAgeMilestone(it.dob, today, windowEnd) == null && PatientUtils.isOlderThanMonths(it.dob, 19, today) }
                .mapNotNull { patient ->
                    val birthMillis = PatientUtils.parseDate(patient.dob)?.time ?: return@mapNotNull null
                    Quadruple(patient, birthMillis, "Older", null)
                }
                .sortedBy { it.second }
        } else {
            patients.mapNotNull { patient ->
                val milestone = PatientUtils.getNextAgeMilestone(patient.dob, today, windowEnd) ?: return@mapNotNull null
                if (milestone.label != label) return@mapNotNull null
                Quadruple(patient, milestone.date.timeInMillis, milestone.label, milestone.date.toInstant().atZone(java.time.ZoneId.systemDefault()).format(dateFormat))
            }.sortedBy { it.second }
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                BackTopAppBar(
                    title = { Text(label) },
                    onBack = onBack
                )
            }
        ) { padding ->
            AppPullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                if (isLoading) {
                    SkeletonList(
                        modifier = Modifier.fillMaxSize(),
                        count = 8,
                        cardShaped = true,
                        spacing = 8.dp,
                        contentPadding = PaddingValues(16.dp)
                    )
                } else if (entries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No patients reaching this milestone in the next 2 months.",
                            modifier = Modifier.padding(24.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(entries, key = { it.first.id }) { (patient, _, milestoneLabel, milestoneDate) ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onPatientClick(patient.id) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(patient.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "DOB: ${PatientUtils.formatDateForDisplay(patient.dob)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (milestoneDate != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "Milestone: $milestoneLabel — $milestoneDate",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
