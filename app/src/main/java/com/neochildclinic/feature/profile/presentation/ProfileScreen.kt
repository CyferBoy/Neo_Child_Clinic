package com.neochildclinic.feature.profile.presentation

import com.neochildclinic.core.designsystem.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.ShowSnackbar
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.StandardTextField
import com.neochildclinic.domain.model.Profile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
val snackbarHostState = remember { SnackbarHostState() }
    var showEditDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    ShowSnackbar(uiState.success, snackbarHostState, duration = SnackbarDuration.Short) {
        showEditDialog = false
        showPasswordDialog = false
        viewModel.clearMessages()
    }
    ShowSnackbar(uiState.error, snackbarHostState) { viewModel.clearMessages() }

    if (showEditDialog && uiState.profile != null) {
        EditProfileDialog(
            profile = uiState.profile!!,
            isLoading = uiState.isLoading,
            onDismiss = { showEditDialog = false },
            onSave = { name, phone ->
                if (name != uiState.profile!!.displayName) viewModel.updateName(name)
                if (phone != uiState.profile!!.phoneNumber) viewModel.updatePhoneNumber(phone)
            }
        )
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            isLoading = uiState.isLoading,
            onDismiss = { showPasswordDialog = false },
            onConfirm = { viewModel.changePassword(it) }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout from this device?") },
            confirmButton = {
                Button(
                    onClick = { 
                        showLogoutDialog = false
                        onLogout() 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Logout") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }

    AppBackground {
        Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
            topBar = {
                BackTopAppBar(
                    title = { Text("My Profile") },
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Default.Edit, "Edit Profile", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                )
            }
        ) { padding ->
            if (uiState.isLoading && uiState.profile == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                uiState.profile?.let { profile ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                    ) {
                        ProfileHeaderSection(profile)
                        
                        InfoSection(title = "Personal Information") {
                            InfoRow(Icons.Default.Person, "Full Name", profile.displayName)
                            InfoRow(Icons.Default.Email, "Email Address", profile.email)
                            InfoRow(Icons.Default.Phone, "Phone Number", profile.phoneNumber.ifBlank { "Not set" })
                        }

                        InfoSection(title = "Account Information") {
                            InfoRow(Icons.Default.Work, "Employee ID", profile.employeeId ?: "Not assigned")
                            InfoRow(Icons.Default.Badge, "Role", profile.role.name.uppercase())
                            InfoRow(Icons.Default.CalendarToday, "Created At", com.neochildclinic.core.common.PatientUtils.formatDateTimeForDisplay(profile.createdAt))
                            InfoRow(Icons.Default.Login, "Last Login", profile.lastLogin?.let { com.neochildclinic.core.common.PatientUtils.formatDateTimeForDisplay(it) } ?: "Never")
                        }

                        InfoSection(title = "Security") {
                            ActionRow(Icons.Default.Lock, "Change Password") { showPasswordDialog = true }
                            ActionRow(Icons.Default.Logout, "Logout", MaterialTheme.colorScheme.error) { showLogoutDialog = true }
                        }

                        InfoSection(title = "About") {
                            InfoRow(Icons.Default.Info, "App Version", "1.0.0")
                            InfoRow(Icons.Default.Cloud, "Database Status", "Connected")
                        }
                        
                        Spacer(Modifier.height(Spacing.xl))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeaderSection(profile: Profile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = profile.displayName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(profile.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(profile.role.name.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            
            Surface(
                color = if (profile.isActive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                shape = CircleShape,
                modifier = Modifier.padding(top = Spacing.sm)
            ) {
                Text(
                    text = if (profile.isActive) "Active" else "Inactive",
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (profile.isActive) Color(0xFF2E7D32) else Color(0xFFC62828)
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
            modifier = Modifier.padding(start = Spacing.sm, bottom = Spacing.sm)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                content()
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.lg))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.lg))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun EditProfileDialog(
    profile: Profile,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(profile.displayName) }
    var phone by remember { mutableStateOf(profile.phoneNumber) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StandardTextField(value = name, onValueChange = { name = it }, label = "Display Name")
                StandardTextField(value = phone, onValueChange = { phone = it }, label = "Phone Number")
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, phone) }, enabled = !isLoading) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ChangePasswordDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StandardTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = "New Password",
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                        }
                    }
                )
                StandardTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = "Confirm New Password",
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newPassword) },
                enabled = !isLoading && newPassword.isNotBlank() && newPassword == confirmPassword
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text("Update")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
