package com.neochildclinic.features.profile

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
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
    val context = LocalContext.current
    
    var showEditDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.success, uiState.error) {
        uiState.success?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            showEditDialog = false
            showPasswordDialog = false
            viewModel.clearMessages()
        }
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

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
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("My Profile") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Default.Edit, "Edit Profile", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary)
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
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            InfoRow(Icons.Default.CalendarToday, "Created At", com.neochildclinic.core.utils.PatientUtils.formatDateTimeForDisplay(profile.createdAt))
                            InfoRow(Icons.Default.Login, "Last Login", profile.lastLogin?.let { com.neochildclinic.core.utils.PatientUtils.formatDateTimeForDisplay(it) } ?: "Never")
                        }

                        InfoSection(title = "Security") {
                            ActionRow(Icons.Default.Lock, "Change Password") { showPasswordDialog = true }
                            ActionRow(Icons.Default.Logout, "Logout", MaterialTheme.colorScheme.error) { showLogoutDialog = true }
                        }

                        InfoSection(title = "About") {
                            InfoRow(Icons.Default.Info, "App Version", "1.0.0")
                            InfoRow(Icons.Default.Cloud, "Database Status", "Connected")
                        }
                        
                        Spacer(Modifier.height(24.dp))
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
                        text = profile.displayName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(profile.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(profile.role.name.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            
            Surface(
                color = if (profile.isActive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                shape = CircleShape,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = if (profile.isActive) "Active" else "Inactive",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
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

@Composable
private fun ActionRow(icon: ImageVector, label: String, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
