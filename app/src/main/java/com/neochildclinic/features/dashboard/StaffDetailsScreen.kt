package com.neochildclinic.features.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.UserRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffDetailsScreen(
    staffId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val staff = remember(uiState.staffList, staffId) {
        uiState.staffList.find { it.id == staffId }
    }
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var showPasswordResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.success, uiState.error) {
        uiState.success?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

    if (showDeleteDialog && staff != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Staff") },
            text = { Text("Are you sure you want to delete ${staff.displayName}? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStaff(staff.id)
                        showDeleteDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    if (showStatusDialog && staff != null) {
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            title = { Text(if (staff.isActive) "Deactivate Staff" else "Activate Staff") },
            text = { Text("Are you sure you want to ${if (staff.isActive) "deactivate" else "activate"} ${staff.displayName}?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.toggleStaffStatus(staff.id, !staff.isActive)
                    showStatusDialog = false
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showStatusDialog = false }) { Text("Cancel") } }
        )
    }

    if (showPasswordResetDialog && staff != null) {
        AlertDialog(
            onDismissRequest = { showPasswordResetDialog = false },
            title = { Text("Reset Password") },
            text = { Text("Send a password reset email to ${staff.email}?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.resetStaffPassword(staff.email)
                    showPasswordResetDialog = false
                }) { Text("Send Email") }
            },
            dismissButton = { TextButton(onClick = { showPasswordResetDialog = false }) { Text("Cancel") } }
        )
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Staff Details") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    actions = {
                        if (staff != null) {
                            IconButton(onClick = { onEdit(staff.id) }) {
                                Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onBackground)
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StaffHeaderSection(staff)

                    InfoSection(title = "Personal Information") {
                        InfoRow(Icons.Default.Person, "Display Name", staff.displayName)
                        InfoRow(Icons.Default.Email, "Email Address", staff.email)
                        InfoRow(Icons.Default.Phone, "Phone Number", staff.phoneNumber.ifBlank { "Not set" })
                        InfoRow(Icons.Default.Badge, "Role", staff.role.name.uppercase())
                        if (!staff.employeeId.isNullOrBlank()) {
                            InfoRow(Icons.Default.Work, "Employee ID", staff.employeeId)
                        }
                    }

                    InfoSection(title = "Account Information") {
                        InfoRow(Icons.Default.Fingerprint, "User UUID", staff.id)
                        InfoRow(Icons.Default.CalendarToday, "Created At", staff.createdAt)
                        InfoRow(Icons.Default.Update, "Last Updated", staff.updatedAt)
                        if (!staff.lastLogin.isNullOrBlank()) {
                            InfoRow(Icons.Default.Login, "Last Login", staff.lastLogin)
                        }
                    }

                    InfoSection(title = "Activity Summary") {
                        // Placeholder for now
                        Text("No recent activity recorded.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(Modifier.height(8.dp))
                    
                    Text("Admin Actions", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            TextButton(
                                onClick = { showPasswordResetDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.LockReset, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Reset Password")
                            }
                            
                            TextButton(
                                onClick = { showStatusDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(contentColor = if (staff.isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(if (staff.isActive) Icons.Default.Block else Icons.Default.CheckCircle, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (staff.isActive) "Deactivate Account" else "Activate Account")
                            }

                            TextButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Delete Staff Member")
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun StaffHeaderSection(staff: Profile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = staff.displayName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(staff.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(staff.role.name.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            
            Surface(
                color = if (staff.isActive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                shape = CircleShape,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = if (staff.isActive) "Active" else "Inactive",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (staff.isActive) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
