package com.clinic.neochild.features.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clinic.neochild.R
import com.clinic.neochild.domain.model.Staff

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTopBar(
    staff: Staff?,
    showManageStaff: Boolean,
    showAuditLogs: Boolean,
    onManageStaff: () -> Unit,
    onSettings: () -> Unit,
    onSync: () -> Unit,
    onAuditLogs: () -> Unit,
    onProfile: () -> Unit,
    onSearch: () -> Unit,
    onLogout: () -> Unit
) {
    var profileMenuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text("Neo Child Clinic", fontWeight = FontWeight.SemiBold) },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onBackground)
            }
            Box {
                IconButton(onClick = { profileMenuExpanded = true }) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Profile",
                            modifier = Modifier.padding(4.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                DropdownMenu(
                    expanded = profileMenuExpanded,
                    onDismissRequest = { profileMenuExpanded = false }
                ) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = staff?.name ?: "User",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("View Profile") },
                        onClick = { 
                            profileMenuExpanded = false
                            onProfile()
                        },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                    )
                    if (showManageStaff) {
                        DropdownMenuItem(
                            text = { Text("Manage Staff") },
                            onClick = {
                                profileMenuExpanded = false
                                onManageStaff()
                            },
                            leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Cloud Sync") },
                        onClick = {
                            profileMenuExpanded = false
                            onSync()
                        },
                        leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null) }
                    )
                    if (showAuditLogs) {
                        DropdownMenuItem(
                            text = { Text("Audit Log") },
                            onClick = {
                                profileMenuExpanded = false
                                onAuditLogs()
                            },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = {
                            profileMenuExpanded = false
                            onSettings()
                        },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Logout") },
                        onClick = {
                            profileMenuExpanded = false
                            onLogout()
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout") }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

@Composable
fun ClinicLogo(isWideScreen: Boolean) {
    val logoSize = if (isWideScreen) 200.dp else 160.dp
    Image(
        painter = painterResource(id = R.drawable.logo),
        contentDescription = "Clinic Logo",
        modifier = Modifier.size(logoSize)
    )
}
