package com.neochildclinic.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.core.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageStaffScreen(
    onBack: () -> Unit,
    onAddStaff: () -> Unit,
    onStaffClick: (String) -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var selectedRoleFilter by remember { mutableStateOf<UserRole?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }

    val filteredStaff = remember(uiState.staffList, searchQuery, selectedRoleFilter) {
        uiState.staffList.filter { staff ->
            val matchesSearch = staff.displayName.contains(searchQuery, ignoreCase = true) || 
                                staff.email.contains(searchQuery, ignoreCase = true)
            val matchesRole = selectedRoleFilter == null || staff.role == selectedRoleFilter
            matchesSearch && matchesRole
        }
    }

    AppBackground {
        Scaffold(
            topBar = {
                SearchTopAppBar(
                    title = "Manage Staff",
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    isSearchActive = isSearchActive,
                    onSearchActiveChange = { isSearchActive = it },
                    onBack = onBack,
                    actions = {
                        Box {
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(Icons.Default.FilterList, "Filter", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("All Roles") },
                                    onClick = { selectedRoleFilter = null; showFilterMenu = false }
                                )
                                UserRole.entries.forEach { role ->
                                    DropdownMenuItem(
                                        text = { Text(role.name.replace("_", " ").uppercase()) },
                                        onClick = { selectedRoleFilter = role; showFilterMenu = false }
                                    )
                                }
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onAddStaff, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Add, "Add Staff")
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(Modifier.padding(padding)) {
                if (selectedRoleFilter != null) {
                    FilterChip(
                        selected = true,
                        onClick = { selectedRoleFilter = null },
                        label = { Text("Role: ${selectedRoleFilter?.name?.uppercase()}") },
                        trailingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) }, // Using Add as Close for now
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                if (uiState.isLoading && uiState.staffList.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                } else if (filteredStaff.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Group, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                            Spacer(Modifier.height(16.dp))
                            Text("No staff members found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredStaff, key = { it.id }) { staff ->
                            StaffCard(staff = staff, onClick = { onStaffClick(staff.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffCard(staff: Profile, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = staff.displayName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(staff.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(staff.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = staff.role.name.uppercase(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(staff.isActive)
                }
            }

            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun StatusBadge(isActive: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = CircleShape,
            color = if (isActive) Color(0xFF4CAF50) else Color(0xFFF44336)
        ) {}
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (isActive) "Active" else "Inactive",
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) Color(0xFF2E7D32) else Color(0xFFC62828)
        )
    }
}
