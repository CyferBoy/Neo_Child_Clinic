package com.neochildclinic.feature.staff.presentation

import com.neochildclinic.core.designsystem.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.ShowSnackbar
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.StandardTextField
import com.neochildclinic.domain.model.UserRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditStaffScreen(
    staffId: String,
    onBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
val snackbarHostState = remember { SnackbarHostState() }
    val staff = remember(uiState.staffList, staffId) {
        uiState.staffList.find { it.id == staffId }
    }

    var name by rememberSaveable(staff) { mutableStateOf(staff?.displayName ?: "") }
    var phone by rememberSaveable(staff) { mutableStateOf(staff?.phoneNumber ?: "") }
    var selectedRole by remember(staff) { mutableStateOf(staff?.role ?: UserRole.nurse) }

    ShowSnackbar(uiState.success, snackbarHostState, duration = SnackbarDuration.Short) {
        viewModel.clearMessages()
        onBack()
    }
    ShowSnackbar(uiState.error, snackbarHostState) { viewModel.clearMessages() }

    AppBackground {
        Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
            topBar = {
                BackTopAppBar(
                    title = { Text("Edit Staff") },
                    onBack = onBack
                )
            }
        ) { padding ->
            if (staff == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    StandardTextField(value = staff.email, onValueChange = {}, label = "Email Address", enabled = false)
                    StandardTextField(value = staff.id, onValueChange = {}, label = "Staff UUID", enabled = false)
                    
                    HorizontalDivider(Modifier.padding(vertical = Spacing.sm))

                    StandardTextField(value = name, onValueChange = { name = it }, label = "Full Name")
                    StandardTextField(value = phone, onValueChange = { phone = it }, label = "Phone Number")

                    Text("Role", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(Modifier.padding(Spacing.sm)) {
                            UserRole.entries.forEach { role ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = selectedRole == role,
                                            onClick = { selectedRole = role }
                                        )
                                        .padding(Spacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selectedRole == role, onClick = { selectedRole = role })
                                    Spacer(Modifier.width(Spacing.md))
                                    Text(role.name.replace("_", " ").uppercase())
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(Spacing.xl))

                    Button(
                        onClick = { viewModel.updateStaffDetails(staffId, name, phone, selectedRole) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = !uiState.isLoading && name.isNotBlank()
                    ) {
                        if (uiState.isLoading) CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Update Staff Details")
                    }
                }
            }
        }
    }
}
