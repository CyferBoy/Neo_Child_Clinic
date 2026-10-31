package com.neochildclinic.feature.profile.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neochildclinic.core.designsystem.Spacing
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.ShowSnackbar
import com.neochildclinic.core.ui.StandardButton
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
            shape = RoundedCornerShape(24.dp),
            title = { Text("Logout", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to logout from this device?") },
            confirmButton = {
                Button(
                    onClick = { 
                        showLogoutDialog = false
                        onLogout() 
                    },
                    shape = RoundedCornerShape(14.dp),
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
                        
                        Text(
                            text = "Personal information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = Spacing.card, vertical = Spacing.xs)) {
                                ProfileDetailRow("Full name", profile.displayName)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                                ProfileDetailRow("Email", profile.email)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                                ProfileDetailRow("Phone", profile.phoneNumber.ifBlank { "Not set" })
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                                ProfileDetailRow("Employee ID", profile.employeeId ?: "Not assigned")
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                                ProfileDetailRow("Role", profile.role.name.replace("_", " ").uppercase())
                            }
                        }

                        Text(
                            text = "Account & Security",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
                                ActionRow(
                                    icon = Icons.Default.Lock,
                                    label = "Change Password",
                                    onClick = { showPasswordDialog = true }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                                ActionRow(
                                    icon = Icons.AutoMirrored.Filled.Logout,
                                    label = "Logout",
                                    color = MaterialTheme.colorScheme.error,
                                    onClick = { showLogoutDialog = true }
                                )
                            }
                        }

                        // Audit trust note
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.card),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.VerifiedUser,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(Spacing.md))
                                Column {
                                    Text(
                                        "Profile changes are audited",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Your name appears on receipts, vaccination logs, and clinical records you sign.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(Spacing.xl)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(68.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = profile.displayName.split(" ")
                            .mapNotNull { it.firstOrNull()?.toString() }
                            .take(2)
                            .joinToString("")
                            .uppercase()
                            .ifEmpty { "?" },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = profile.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(Spacing.xs))
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "${profile.role.name.uppercase()} · ${if (profile.isActive) "ACTIVE" else "INACTIVE"}",
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun ProfileDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.card, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Default.ChevronRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
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
        shape = RoundedCornerShape(24.dp),
        title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StandardTextField(value = name, onValueChange = { name = it }, label = "Display Name")
                StandardTextField(value = phone, onValueChange = { phone = it }, label = "Phone Number")
            }
        },
        confirmButton = {
            StandardButton(onClick = { onSave(name, phone) }, isLoading = isLoading) {
                Text("Save")
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
        shape = RoundedCornerShape(24.dp),
        title = { Text("Change Password", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StandardTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = "New Password",
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (visible) "Hide password" else "Show password"
                            )
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
            StandardButton(
                onClick = { onConfirm(newPassword) },
                isLoading = isLoading,
                enabled = !isLoading && newPassword.isNotBlank() && newPassword == confirmPassword
            ) {
                Text("Update")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
