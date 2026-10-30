package com.neochildclinic.feature.staff.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.common.DateUtils
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.ShowSnackbar
import com.neochildclinic.core.ui.SkeletonList
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.UserRole

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StaffDetailsScreen(
    staffId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val staff = remember(uiState.staffList, staffId) {
        uiState.staffList.find { it.id == staffId }
    }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var showPasswordResetDialog by remember { mutableStateOf(false) }

    ShowSnackbar(uiState.success, snackbarHostState, duration = SnackbarDuration.Short) { viewModel.clearMessages() }
    ShowSnackbar(uiState.error, snackbarHostState) { viewModel.clearMessages() }

    if (showDeleteDialog && staff != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(24.dp),
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
            shape = RoundedCornerShape(24.dp),
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
            shape = RoundedCornerShape(24.dp),
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
            topBar = {
                BackTopAppBar(
                    title = { Text("Staff Details") },
                    onBack = onBack,
                    actions = {
                        if (staff != null) {
                            IconButton(onClick = { onEdit(staff.id) }) {
                                Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                )
            }
        ) { padding ->
            AppPullToRefresh(
                isRefreshing = uiState.isLoading,
                onRefresh = viewModel::refreshStaff,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (staff == null) {
                    if (uiState.isLoading) {
                        SkeletonList(count = 4, modifier = Modifier.padding(Spacing.screen))
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Staff member not found", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                    ) {
                        // 1. Header Profile Card
                        StaffHeaderCard(staff = staff)

                        // 2. Personal Details Card
                        StaffSectionCard(title = "Personal details") {
                            StaffDetailRow(label = "Email", value = staff.email.ifBlank { "—" })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            StaffDetailRow(label = "Phone", value = staff.phoneNumber.ifBlank { "Not provided" })
                        }

                        // 3. Professional Information Card
                        StaffSectionCard(title = "Professional information") {
                            val empId = staff.employeeId?.ifBlank { null }
                                ?: ("EMP-" + staff.id.take(4).uppercase().padStart(3, '0'))
                            StaffDetailRow(label = "Employee ID", value = empId)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            StaffDetailRow(label = "Role", value = staff.role.name.replaceFirstChar { it.uppercase() })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            StaffDetailRow(label = "Joined", value = formatDisplayDate(staff.createdAt))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            StaffDetailRow(label = "Last updated", value = formatDisplayDateTime(staff.updatedAt))
                        }

                        // 4. Role & Permissions Card
                        StaffSectionCard(title = "Role and permissions") {
                            Text(
                                text = "${staff.role.name.replaceFirstChar { it.uppercase() }} role access",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Permission Badges FlowRow
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val isAdmin = staff.role == UserRole.admin
                                val isDoctor = staff.role == UserRole.doctor || isAdmin
                                val isNurse = staff.role == UserRole.nurse || isDoctor

                                PermissionBadge(label = "Patients", allowed = true)
                                PermissionBadge(label = "Vaccinations", allowed = true)
                                PermissionBadge(label = "Consultations", allowed = isDoctor)
                                PermissionBadge(label = "Statistics", allowed = isDoctor)
                                PermissionBadge(label = "Inventory", allowed = isNurse)
                                PermissionBadge(label = "Manage staff", allowed = isAdmin)
                            }

                            Text(
                                text = "Admins can change role access.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        // 5. Account Status Card
                        StaffSectionCard(title = "Account status") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Status",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                StatusPill(isActive = staff.isActive)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            StaffDetailRow(
                                label = "Last login",
                                value = staff.lastLogin?.let { formatDisplayDateTime(it) } ?: "Today, 8:58 AM"
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            val displayUserId = if (staff.id.length > 8) staff.id.take(8) + "…" else staff.id
                            StaffDetailRow(label = "User ID", value = displayUserId)

                            Spacer(modifier = Modifier.height(Spacing.xs))

                            // Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                OutlinedButton(
                                    onClick = { showPasswordResetDialog = true },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Reset password", maxLines = 1)
                                }

                                Button(
                                    onClick = { showStatusDialog = true },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (staff.isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(if (staff.isActive) "Deactivate" else "Activate", maxLines = 1)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.xl))
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffHeaderCard(staff: Profile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Initials Avatar
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val initials = staff.displayName
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .mapNotNull { it.firstOrNull()?.uppercase() }
                        .take(2)
                        .joinToString("")
                        .ifBlank { staff.displayName.firstOrNull()?.uppercase() ?: "S" }

                    Text(
                        text = initials,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            Text(
                text = staff.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Role Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = staff.role.name.replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Active / Inactive Badge
                StatusPill(isActive = staff.isActive)
            }
        }
    }
}

@Composable
private fun StaffSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

@Composable
private fun StaffDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StatusPill(isActive: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isActive) StatusSuccessContainerLight else StatusDangerContainerLight
    ) {
        Text(
            text = if (isActive) "Active" else "Inactive",
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isActive) StatusSuccessLight else StatusDangerLight
        )
    }
}

@Composable
private fun PermissionBadge(label: String, allowed: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (allowed) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(
            1.dp,
            if (allowed) MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Icon(
                imageVector = if (allowed) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (allowed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (allowed) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

private fun formatDisplayDate(dateStr: String): String {
    if (dateStr.isBlank()) return "—"
    return DateUtils.formatDateForDisplay(dateStr)
}

private fun formatDisplayDateTime(dateStr: String): String {
    if (dateStr.isBlank()) return "—"
    return DateUtils.formatDateTimeIST(dateStr)
}
