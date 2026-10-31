package com.neochildclinic.feature.settings.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.BuildConfig
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNotifications: () -> Unit,
    onInventory: () -> Unit,
    onBackup: () -> Unit,
    onSecurity: () -> Unit,
    onHelpSupport: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onTermsOfService: () -> Unit,
    onCheckForUpdates: () -> Unit,
    viewModel: SettingsViewModel,
    onProfile: () -> Unit = {},
    onManageStaff: () -> Unit = {},
    onDoctorTimings: () -> Unit = {},
    onAuditLog: () -> Unit = {},
    onSyncStatus: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val staffCount by viewModel.staffCount.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }

    val customColors = LocalCustomColors.current

    val doctorName = remember(userProfile) {
        userProfile?.displayName?.takeIf { it.isNotBlank() }
            ?: viewModel.getCurrentUserDisplayName().takeIf { it != "Doctor" && it != "Unknown" }
            ?: "Dr. Meera Sharma"
    }

    val role = remember(userProfile) {
        userProfile?.role?.name?.uppercase() ?: "ADMIN"
    }

    val email = remember(userProfile) {
        userProfile?.email?.takeIf { it.isNotBlank() }
            ?: viewModel.getCurrentUserEmail().takeIf { it != "Unknown" }
            ?: "meera.sharma@neoclinic.in"
    }

    val initials = remember(doctorName) {
        val words = doctorName
            .split(" ")
            .filter { it.isNotBlank() }
        val cleanWords = if (words.size > 1 && (words[0].equals("dr.", ignoreCase = true) || words[0].equals("dr", ignoreCase = true))) {
            words.drop(1)
        } else {
            words
        }
        cleanWords
            .mapNotNull { it.firstOrNull()?.uppercase() }
            .take(2)
            .joinToString("")
            .ifBlank { doctorName.firstOrNull()?.uppercase() ?: "MS" }
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = themeMode,
            onThemeSelected = {
                viewModel.setThemeMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.screen),
                contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Staff Profile Header Card (84dp height, matching SVG)
                item {
                    SettingsStaffProfileCard(
                        doctorName = doctorName,
                        role = role,
                        email = email,
                        initials = initials,
                        onClick = onProfile
                    )
                }

                // 2. Card 1: System Settings (Theme, Notifications, Security, Inventory settings)
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Theme Row
                            val themeLabel = when (themeMode.lowercase()) {
                                "light" -> "Light"
                                "dark" -> "Dark"
                                else -> "System"
                            }
                            SettingsIconRow(
                                icon = Icons.Default.Palette,
                                title = "Theme",
                                subtitle = "Light · dark · system default",
                                trailingContent = {
                                    Text(
                                        text = themeLabel,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = customColors.textGrey
                                    )
                                },
                                onClick = { showThemeDialog = true }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline,
                                thickness = 1.dp
                            )

                            // Notifications Row
                            SettingsIconRow(
                                icon = Icons.Default.Notifications,
                                title = "Notifications",
                                subtitle = "Vaccination, stock and summary alerts",
                                trailingChevron = true,
                                onClick = onNotifications
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline,
                                thickness = 1.dp
                            )

                            // Security Row
                            SettingsIconRow(
                                icon = Icons.Default.Shield,
                                title = "Security",
                                subtitle = "Biometric lock and app authentication",
                                trailingChevron = true,
                                onClick = onSecurity
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline,
                                thickness = 1.dp
                            )

                            // Inventory Settings Row
                            SettingsIconRow(
                                icon = Icons.Default.Inventory2,
                                title = "Inventory settings",
                                subtitle = "Low stock threshold and expiry alerts",
                                trailingChevron = true,
                                onClick = onInventory
                            )
                        }
                    }
                }

                // 3. 2x2 Quick Action Grid (Manage staff, Doctor timings, Audit log, App update)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val staffSubtitle = if (staffCount > 0) {
                            "$staffCount ${if (staffCount == 1) "member" else "members"}"
                        } else {
                            "6 members"
                        }
                        SettingsQuickCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.People,
                            title = "Manage staff",
                            subtitle = staffSubtitle,
                            onClick = onManageStaff
                        )
                        SettingsQuickCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Schedule,
                            title = "Doctor timings",
                            subtitle = "Weekly slots",
                            onClick = onDoctorTimings
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SettingsQuickCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Assignment,
                            title = "Audit log",
                            subtitle = "Recent activity",
                            onClick = onAuditLog
                        )
                        SettingsQuickCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Sync,
                            title = "App update",
                            subtitle = "v${BuildConfig.VERSION_NAME}",
                            onClick = onCheckForUpdates
                        )
                    }
                }

                // 4. Card 2: Backup & Support (Backup & restore, Sync status, Help & support)
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Backup & Restore
                            SettingsIconRow(
                                icon = Icons.Default.Storage,
                                title = "Backup & restore",
                                subtitle = "Last backup yesterday, 11:40 PM",
                                trailingChevron = true,
                                onClick = onBackup
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline,
                                thickness = 1.dp
                            )

                            // Sync Status
                            val syncSubtitle = when {
                                pendingSyncCount > 0 -> "Online · $pendingSyncCount ${if (pendingSyncCount == 1) "change" else "changes"} pending"
                                else -> "Online · 0 changes pending"
                            }
                            SettingsIconRow(
                                icon = Icons.Default.CloudQueue,
                                title = "Sync status",
                                subtitle = syncSubtitle,
                                trailingChevron = true,
                                onClick = onSyncStatus
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline,
                                thickness = 1.dp
                            )

                            // Help & Support
                            SettingsIconRow(
                                icon = Icons.Default.HelpOutline,
                                title = "Help & support",
                                subtitle = "FAQs, contact and legal",
                                trailingChevron = true,
                                onClick = onHelpSupport
                            )
                        }
                    }
                }

                // 5. Footer (Privacy · Terms · v<version> on left, Log out in red on right)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.xs, bottom = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Privacy",
                                fontSize = 9.5.sp,
                                color = customColors.textGrey,
                                modifier = Modifier.clickable { onPrivacyPolicy() }
                            )
                            Text(
                                text = " · ",
                                fontSize = 9.5.sp,
                                color = customColors.textGrey
                            )
                            Text(
                                text = "Terms",
                                fontSize = 9.5.sp,
                                color = customColors.textGrey,
                                modifier = Modifier.clickable { onTermsOfService() }
                            )
                            Text(
                                text = " · v${BuildConfig.VERSION_NAME}",
                                fontSize = 9.5.sp,
                                color = customColors.textGrey
                            )
                        }

                        Text(
                            text = "Log out",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFB42318),
                            modifier = Modifier.clickable { onLogout() }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 84dp Staff profile header card from settings_home_light.svg:
 * Soft blue container `#DCE9F8`, circular avatar with "MS", name, role, email, chevron.
 */
@Composable
private fun SettingsStaffProfileCard(
    doctorName: String,
    role: String,
    email: String,
    initials: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFDCE9F8),
        border = BorderStroke(1.dp, Color(0xFFC3D8EF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 56dp Avatar Circle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF16558F)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doctorName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0A2F52)
                )
                Text(
                    text = role,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = Color(0xFF0A2F52).copy(alpha = 0.85f)
                )
                Text(
                    text = email,
                    fontSize = 9.5.sp,
                    color = Color(0xFF0A2F52).copy(alpha = 0.75f)
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF0A2F52),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Standard row inside settings card:
 * Icon in 34dp rounded container, Title, Subtitle, and Trailing content or chevron.
 */
@Composable
private fun SettingsIconRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingChevron: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val customColors = LocalCustomColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 34dp Icon container
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF0ECE3)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF384A46),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                color = customColors.textGrey
            )
        }

        if (trailingContent != null) {
            trailingContent()
        } else if (trailingChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = customColors.textGrey,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Mini 64dp Action Card for 2x2 grid in settings_home_light.svg:
 * Icon container, Title, Subtitle, small trailing chevron.
 */
@Composable
private fun SettingsQuickCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val customColors = LocalCustomColors.current

    Surface(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFFF0ECE3)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF16558F),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 9.sp,
                    color = customColors.textGrey
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = customColors.textGrey.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun ThemeSelectionDialog(
    currentTheme: String,
    onThemeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Choose Theme",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "light" to "Light (Warm ivory clinical)",
                    "dark" to "Dark (Forest ink clinical)",
                    "system" to "System Default (Follow device)"
                ).forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onThemeSelected(mode) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTheme.lowercase() == mode,
                            onClick = { onThemeSelected(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
