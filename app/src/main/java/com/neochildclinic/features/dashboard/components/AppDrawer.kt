package com.neochildclinic.features.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.domain.repository.SyncState

@Composable
fun AppDrawer(
    userName: String,
    userRole: UserRole,
    syncState: SyncState,
    appVersion: String,
    onProfileClick: () -> Unit,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit,
    currentRoute: String? = null
) {
    ModalDrawerSheet(
        modifier = Modifier.width(320.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        // Drawer Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { onProfileClick() }
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(60.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = userName.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = userRole.name.replace("_", " ").uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(NavigationDrawerItemDefaults.ItemPadding)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Dashboard
            DrawerMenuItem(
                label = "Dashboard",
                icon = Icons.Default.Dashboard,
                isSelected = currentRoute == "dashboard",
                onClick = { onNavigate("dashboard") }
            )

            // Patients
            if (userRole in listOf(UserRole.admin, UserRole.doctor, UserRole.receptionist, UserRole.nurse)) {
                DrawerMenuItem(
                    label = "Patients",
                    icon = Icons.Default.People,
                    isSelected = currentRoute == "patient_list",
                    onClick = { onNavigate("patient_list") }
                )
            }

            // Due Vaccinations
            if (userRole in listOf(UserRole.admin, UserRole.doctor, UserRole.receptionist, UserRole.nurse)) {
                DrawerMenuItem(
                    label = "Due Vaccinations",
                    icon = Icons.Default.EventNote,
                    isSelected = currentRoute == "due",
                    onClick = { onNavigate("due") }
                )
            }

            // Consultations
            if (userRole in listOf(UserRole.admin, UserRole.doctor)) {
                DrawerMenuItem(
                    label = "Consultations",
                    icon = Icons.Default.MedicalServices,
                    onClick = { /* Navigate to Consultations when implemented */ }
                )
            }

            // Inventory
            if (userRole in listOf(UserRole.admin, UserRole.inventory_manager, UserRole.doctor)) {
                DrawerMenuItem(
                    label = "Inventory",
                    icon = Icons.Default.Inventory,
                    isSelected = currentRoute == "vaccine_inventory",
                    onClick = { onNavigate("vaccine_inventory") }
                )
            }

            // Billing
            if (userRole in listOf(UserRole.admin, UserRole.doctor, UserRole.receptionist)) {
                DrawerMenuItem(
                    label = "Billing",
                    icon = Icons.Default.Payments,
                    onClick = { /* Navigate to Billing when implemented */ }
                )
            }

            // Reports
            if (userRole in listOf(UserRole.admin, UserRole.doctor, UserRole.inventory_manager)) {
                DrawerMenuItem(
                    label = "Reports",
                    icon = Icons.Default.Assessment,
                    isSelected = currentRoute == "statistics",
                    onClick = { onNavigate("statistics") }
                )
            }

            // Manage Staff
            if (userRole == UserRole.admin) {
                DrawerMenuItem(
                    label = "Manage Staff",
                    icon = Icons.Default.AdminPanelSettings,
                    isSelected = currentRoute == "manage_staff",
                    onClick = { onNavigate("manage_staff") }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Help & Support
            DrawerMenuItem(
                label = "Help & Support",
                icon = Icons.Default.Help,
                onClick = { /* Support Logic */ }
            )
        }

        // Drawer Footer
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, "Settings")
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onSyncClick() }
                        .padding(8.dp)
                ) {
                    val (icon, color) = when (syncState) {
                        SyncState.SYNCING -> Icons.Default.Sync to MaterialTheme.colorScheme.primary
                        SyncState.ERROR -> Icons.Default.CloudOff to MaterialTheme.colorScheme.error
                        else -> Icons.Default.CloudDone to Color(0xFF4CAF50)
                    }
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (syncState == SyncState.ERROR) "Offline" else "Online",
                        style = MaterialTheme.typography.labelMedium,
                        color = color
                    )
                }

                IconButton(onClick = onLogout) {
                    Icon(Icons.AutoMirrored.Filled.Logout, "Logout", tint = MaterialTheme.colorScheme.error)
                }
            }
            
            Text(
                text = "v$appVersion",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun DrawerMenuItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
        selected = isSelected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = Color.Transparent,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}
