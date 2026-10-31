package com.neochildclinic.feature.staff.presentation

import com.neochildclinic.core.designsystem.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import com.neochildclinic.core.ui.StandardButton
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.ShowSnackbar
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.StandardTextField
import com.neochildclinic.domain.model.UserRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStaffScreen(
    onBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

val snackbarHostState = remember { SnackbarHostState() }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var employeeId by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.nurse) }
    var passwordVisible by remember { mutableStateOf(false) }

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
                    title = { Text("Add New Staff") },
                    onBack = onBack
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                StandardTextField(value = name, onValueChange = { name = it }, label = "Full Name", placeholder = "Rahul Kumar")
                StandardTextField(value = email, onValueChange = { email = it }, label = "Email Address", placeholder = "rahul@gmail.com")
                StandardTextField(value = phone, onValueChange = { phone = it }, label = "Phone Number", placeholder = "+91 9876543210")
                StandardTextField(value = employeeId, onValueChange = { employeeId = it }, label = "Employee ID", placeholder = "EMP-001")
                
                StandardTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Temporary Password",
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    }
                )

                Text("Role", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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

                StandardButton(
                    onClick = { viewModel.createStaffAccount(name, email, password, selectedRole, employeeId.ifBlank { null }, phone.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = uiState.isLoading,
                    enabled = !uiState.isLoading && name.isNotBlank() && email.isNotBlank() && password.isNotBlank()
                ) {
                    Text("Create Staff Account")
                }
            }
        }
    }
}
