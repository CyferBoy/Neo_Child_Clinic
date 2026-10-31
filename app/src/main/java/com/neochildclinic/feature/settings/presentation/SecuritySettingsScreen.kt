package com.neochildclinic.feature.settings.presentation

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.security.BiometricAuthenticator
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.StandardButton
import com.neochildclinic.core.ui.StandardTextField
import kotlinx.coroutines.launch

@Composable
fun SecuritySettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val customColors = LocalCustomColors.current
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val isDark = isSystemInDarkTheme()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showPasswordDialog by remember { mutableStateOf(false) }
    var showInactivityDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var isPasswordLoading by remember { mutableStateOf(false) }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { SettingsDetailTopBar("Security", onBack) },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            settings?.let { s ->
                val deviceName = remember {
                    val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
                    val model = Build.MODEL
                    if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Banner 1: Device Protection Status
                    item {
                        ProtectionStatusBanner(
                            isProtected = s.biometricLockEnabled,
                            inactivityDays = s.inactivityDaysThreshold,
                            customColors = customColors,
                            isDark = isDark
                        )
                    }

                    // Card 1: Biometric & Inactivity Lock
                    item {
                        SecurityGroupCard(customColors = customColors) {
                            // Row 1: Biometric lock
                            SecurityRow(
                                icon = Icons.Default.Fingerprint,
                                title = "Biometric lock",
                                subtitle = "Enable fingerprint/face ID",
                                customColors = customColors
                            ) {
                                Switch(
                                    checked = s.biometricLockEnabled,
                                    onCheckedChange = { requested ->
                                        if (requested) {
                                            viewModel.setBiometricLock(true)
                                            viewModel.updateSettings(s.copy(biometricLockEnabled = true))
                                        } else if (activity != null) {
                                            BiometricAuthenticator.authenticate(
                                                activity = activity,
                                                title = "Disable Biometric Lock",
                                                subtitle = "Authenticate to change security settings"
                                            ) {
                                                viewModel.setBiometricLock(false)
                                                viewModel.updateSettings(s.copy(biometricLockEnabled = false))
                                            }
                                        }
                                    },
                                    colors = getSwitchColors(isDark, customColors)
                                )
                            }

                            SecurityDivider(customColors = customColors)

                            // Row 2: Always authenticate
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = s.biometricLockEnabled) {
                                        val requested = !s.authOnEveryOpen
                                        if (requested || activity == null) {
                                            viewModel.updateSettings(s.copy(authOnEveryOpen = requested))
                                        } else {
                                            BiometricAuthenticator.authenticate(
                                                activity = activity,
                                                title = "Change Authentication Setting",
                                                subtitle = "Authenticate to reduce app security"
                                            ) {
                                                viewModel.updateSettings(s.copy(authOnEveryOpen = false))
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Always authenticate",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (s.biometricLockEnabled) customColors.ink else customColors.muted
                                    )
                                    Text(
                                        text = "Auth on every app open",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = customColors.muted
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Switch(
                                    checked = s.authOnEveryOpen,
                                    enabled = s.biometricLockEnabled,
                                    onCheckedChange = { requested ->
                                        if (!s.authOnEveryOpen || activity == null) {
                                            viewModel.updateSettings(s.copy(authOnEveryOpen = requested))
                                        } else if (!requested) {
                                            BiometricAuthenticator.authenticate(
                                                activity = activity,
                                                title = "Change Authentication Setting",
                                                subtitle = "Authenticate to reduce app security"
                                            ) {
                                                viewModel.updateSettings(s.copy(authOnEveryOpen = false))
                                            }
                                        }
                                    },
                                    colors = getSwitchColors(isDark, customColors)
                                )
                            }

                            SecurityDivider(customColors = customColors)

                            // Row 3: Inactivity days
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = s.biometricLockEnabled) { showInactivityDialog = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Inactivity days",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (s.biometricLockEnabled) customColors.ink else customColors.muted,
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    text = "${s.inactivityDaysThreshold} days",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = customColors.muted
                                )
                            }
                        }
                    }

                    // Card 2: Account Security Actions
                    item {
                        SecurityGroupCard(customColors = customColors) {
                            // Row 1: Change password
                            SecurityActionRow(
                                icon = Icons.Default.VpnKey,
                                title = "Change password",
                                subtitle = "Update account login password",
                                customColors = customColors,
                                onClick = { showPasswordDialog = true }
                            )

                            SecurityDivider(customColors = customColors)

                            // Row 2: Sign out of all devices
                            SecurityActionRow(
                                icon = Icons.AutoMirrored.Filled.Logout,
                                title = "Sign out of all devices",
                                subtitle = "Revoke sessions across devices",
                                customColors = customColors,
                                onClick = { showSignOutDialog = true }
                            )
                        }
                    }

                    // Card 3: Active Sessions / Devices
                    item {
                        SecurityGroupCard(customColors = customColors) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Device 1
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(customColors.softBlue, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Smartphone,
                                            contentDescription = null,
                                            tint = customColors.textBlue,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "$deviceName · this device",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = customColors.ink
                                        )
                                        Text(
                                            text = "Active now · Clinic app",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = customColors.muted
                                        )
                                    }
                                }

                                // Device 2
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(customColors.softGrey, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Computer,
                                            contentDescription = null,
                                            tint = customColors.textGrey,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Chrome · clinic desktop",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = customColors.ink
                                        )
                                        Text(
                                            text = "Yesterday, 6:04 PM · Web portal",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = customColors.muted
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Warning Banner: Re-authentication Warning
                    item {
                        ReAuthWarningBanner(customColors = customColors)
                    }
                }

                // Inactivity Dialog
                if (showInactivityDialog) {
                    InactivityThresholdDialog(
                        currentDays = s.inactivityDaysThreshold,
                        onDismiss = { showInactivityDialog = false },
                        onSelect = { selectedDays ->
                            showInactivityDialog = false
                            if (selectedDays <= s.inactivityDaysThreshold || activity == null) {
                                viewModel.updateSettings(s.copy(inactivityDaysThreshold = selectedDays))
                            } else {
                                BiometricAuthenticator.authenticate(
                                    activity = activity,
                                    title = "Change Security Setting",
                                    subtitle = "Authenticate to increase inactivity timeout"
                                ) {
                                    viewModel.updateSettings(s.copy(inactivityDaysThreshold = selectedDays))
                                }
                            }
                        }
                    )
                }

                // Change Password Dialog
                if (showPasswordDialog) {
                    SecurityChangePasswordDialog(
                        isLoading = isPasswordLoading,
                        onDismiss = { showPasswordDialog = false },
                        onConfirm = { newPassword ->
                            isPasswordLoading = true
                            viewModel.changePassword(newPassword) { result ->
                                isPasswordLoading = false
                                result.fold(
                                    onSuccess = {
                                        showPasswordDialog = false
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Password updated successfully")
                                        }
                                    },
                                    onFailure = { error ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(error.message ?: "Failed to update password")
                                        }
                                    }
                                )
                            }
                        }
                    )
                }

                // Sign Out Confirmation Dialog
                if (showSignOutDialog) {
                    AlertDialog(
                        onDismissRequest = { showSignOutDialog = false },
                        shape = RoundedCornerShape(20.dp),
                        title = {
                            Text("Sign out of all devices", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        },
                        text = {
                            Text(
                                "This will invalidate active login sessions on all devices including desktop and tablets.",
                                fontSize = 13.sp,
                                color = customColors.muted
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showSignOutDialog = false
                                    viewModel.signOutAllDevices { result ->
                                        result.fold(
                                            onSuccess = {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Signed out of all devices")
                                                }
                                            },
                                            onFailure = { error ->
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(error.message ?: "Failed to sign out")
                                                }
                                            }
                                        )
                                    }
                                }
                            ) {
                                Text("Sign Out All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSignOutDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ProtectionStatusBanner(
    isProtected: Boolean,
    inactivityDays: Int,
    customColors: CustomColors,
    isDark: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isProtected) customColors.successContainer else customColors.softPink
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (isDark) MaterialTheme.colorScheme.surface else Color.White,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isProtected) Icons.Default.VerifiedUser else Icons.Default.GppMaybe,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isProtected) {
                        if (isDark) StatusSuccessDark else StatusSuccessLight
                    } else {
                        if (isDark) StatusDangerDark else StatusDangerLight
                    }
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isProtected) "This device is protected" else "Device protection is disabled",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isProtected) customColors.textSuccess else customColors.textPink
                )
                Text(
                    text = if (isProtected) "Biometric lock · $inactivityDays days inactivity" else "Biometric lock is disabled",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (isProtected) customColors.textSuccess.copy(alpha = 0.85f) else customColors.textPink.copy(alpha = 0.85f)
                )
                Text(
                    text = if (isProtected) "Biometric authentication active" else "Authenticate to enable device lock",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (isProtected) customColors.textSuccess.copy(alpha = 0.70f) else customColors.textPink.copy(alpha = 0.70f)
                )
            }
        }
    }
}

@Composable
private fun SecurityGroupCard(
    customColors: CustomColors,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, customColors.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun SecurityRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    customColors: CustomColors,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(customColors.surfaceAlt, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = customColors.inkSoft
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = customColors.ink
            )
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Normal,
                color = customColors.muted
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        trailing()
    }
}

@Composable
private fun SecurityActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    customColors: CustomColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(customColors.surfaceAlt, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = customColors.inkSoft
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = customColors.ink
            )
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Normal,
                color = customColors.muted
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = customColors.outlineStrong,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SecurityDivider(customColors: CustomColors) {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = customColors.outline
    )
}

@Composable
private fun ReAuthWarningBanner(customColors: CustomColors) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = customColors.warningContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = customColors.textWarning,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Reducing security requires re-authentication",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = customColors.textWarning
                )
                Text(
                    text = "You will be asked to confirm before lock settings are weakened",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Normal,
                    color = customColors.textWarning.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun InactivityThresholdDialog(
    currentDays: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val options = listOf(1, 3, 7, 10, 15, 30)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text("Inactivity Period", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { days ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(days) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (currentDays == days),
                            onClick = { onSelect(days) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$days day${if (days > 1) "s" else ""}",
                            style = MaterialTheme.typography.bodyLarge
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

@Composable
private fun SecurityChangePasswordDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Change Password", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StandardTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = "New Password (min 12 chars)",
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
                enabled = !isLoading && newPassword.length >= 12 && newPassword == confirmPassword
            ) {
                Text("Update Password")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun getSwitchColors(isDark: Boolean, customColors: CustomColors) = SwitchDefaults.colors(
    checkedThumbColor = if (isDark) Color(0xFF07305B) else Color.White,
    checkedTrackColor = if (isDark) Color(0xFF9CC7FF) else ClinicBlue,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = if (isDark) Color(0xFF151F1B) else Color.White,
    uncheckedTrackColor = customColors.surfaceSunken,
    uncheckedBorderColor = customColors.outlineStrong
)
