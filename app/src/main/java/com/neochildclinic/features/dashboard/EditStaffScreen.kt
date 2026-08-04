package com.neochildclinic.features.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
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
    val staff = remember(uiState.staffList, staffId) {
        uiState.staffList.find { it.id == staffId }
    }
    val context = LocalContext.current

    var name by rememberSaveable(staff) { mutableStateOf(staff?.displayName ?: "") }
    var phone by rememberSaveable(staff) { mutableStateOf(staff?.phoneNumber ?: "") }
    var selectedRole by remember(staff) { mutableStateOf(staff?.role ?: UserRole.nurse) }

    LaunchedEffect(uiState.success, uiState.error) {
        uiState.success?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
            onBack()
        }
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Edit Staff") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary)
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
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StandardTextField(value = staff.email, onValueChange = {}, label = "Email Address", enabled = false)
                    StandardTextField(value = staff.id, onValueChange = {}, label = "Staff UUID", enabled = false)
                    
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    StandardTextField(value = name, onValueChange = { name = it }, label = "Full Name")
                    StandardTextField(value = phone, onValueChange = { phone = it }, label = "Phone Number")

                    Text("Role", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            UserRole.entries.forEach { role ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = selectedRole == role,
                                            onClick = { selectedRole = role }
                                        )
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selectedRole == role, onClick = { selectedRole = role })
                                    Spacer(Modifier.width(12.dp))
                                    Text(role.name.replace("_", " ").uppercase())
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

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
