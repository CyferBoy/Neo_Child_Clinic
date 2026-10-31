package com.neochildclinic.feature.settings.presentation

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground
import java.util.Locale

@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val customColors = LocalCustomColors.current
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { SettingsDetailTopBar("Notifications", onBack) }
        ) { padding ->
            settings?.let { s ->
                val timeParts = remember(s.reminderTime) { s.reminderTime.split(":") }
                val currentHour = remember(timeParts) { timeParts.getOrNull(0)?.toIntOrNull() ?: 8 }
                val currentMinute = remember(timeParts) { timeParts.getOrNull(1)?.toIntOrNull() ?: 0 }

                val showTimePicker = {
                    TimePickerDialog(
                        context,
                        { _, selectedHour, selectedMinute ->
                            val formatted = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
                            viewModel.updateSettings(s.copy(reminderTime = formatted))
                        },
                        currentHour,
                        currentMinute,
                        false
                    ).show()
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Group 1: Due & Overdue vaccinations, Personal reminders
                    item {
                        NotificationGroupCard(customColors = customColors) {
                            NotificationSwitchRow(
                                icon = Icons.Default.Vaccines,
                                title = "Due and overdue vaccinations",
                                subtitle = "Daily at 9:00 AM",
                                checked = s.dueVaccinationsEnabled,
                                customColors = customColors,
                                isDark = isDark,
                                onCheckedChange = { viewModel.updateSettings(s.copy(dueVaccinationsEnabled = it)) }
                            )
                            NotificationDivider(customColors = customColors)
                            NotificationSwitchRow(
                                icon = Icons.Default.Notifications,
                                title = "Personal reminders",
                                subtitle = "When a reminder becomes ready",
                                checked = s.personalRemindersEnabled,
                                customColors = customColors,
                                isDark = isDark,
                                onCheckedChange = { viewModel.updateSettings(s.copy(personalRemindersEnabled = it)) }
                            )
                        }
                    }

                    // Group 2: Low stock, Expiring batches, Failed sync alerts
                    item {
                        NotificationGroupCard(customColors = customColors) {
                            NotificationSwitchRow(
                                icon = Icons.Default.Inventory2,
                                title = "Low stock alerts",
                                subtitle = "When any batch drops below threshold",
                                checked = s.lowStockEnabled,
                                customColors = customColors,
                                isDark = isDark,
                                onCheckedChange = { viewModel.updateSettings(s.copy(lowStockEnabled = it)) }
                            )
                            NotificationDivider(customColors = customColors)
                            NotificationSwitchRow(
                                icon = Icons.Default.Schedule,
                                title = "Expiring batches",
                                subtitle = "Warn ${s.warnDaysBeforeExpiry} days before expiry",
                                checked = s.expiryWarningsEnabled,
                                customColors = customColors,
                                isDark = isDark,
                                onCheckedChange = { viewModel.updateSettings(s.copy(expiryWarningsEnabled = it)) }
                            )
                            NotificationDivider(customColors = customColors)
                            NotificationSwitchRow(
                                icon = Icons.Default.Warning,
                                title = "Failed sync alerts",
                                subtitle = "When a task fails twice",
                                checked = s.syncAlertsEnabled,
                                customColors = customColors,
                                isDark = isDark,
                                onCheckedChange = { viewModel.updateSettings(s.copy(syncAlertsEnabled = it)) }
                            )
                        }
                    }

                    // Group 3: Daily summary, Summary time, App update alerts
                    item {
                        NotificationGroupCard(customColors = customColors) {
                            NotificationSwitchRow(
                                icon = Icons.Default.BarChart,
                                title = "Daily summary",
                                subtitle = "Patients, vaccinations and revenue",
                                checked = s.dailySummaryEnabled,
                                customColors = customColors,
                                isDark = isDark,
                                onCheckedChange = { viewModel.updateSettings(s.copy(dailySummaryEnabled = it)) }
                            )
                            NotificationDivider(customColors = customColors)
                            NotificationActionRow(
                                icon = Icons.Default.Schedule,
                                title = "Summary time",
                                value = formatTimeForDisplay(s.reminderTime),
                                customColors = customColors,
                                onClick = showTimePicker
                            )
                            NotificationDivider(customColors = customColors)
                            NotificationSwitchRow(
                                icon = Icons.Default.SystemUpdate,
                                title = "App update alerts",
                                subtitle = "Notify when a new version is available",
                                checked = s.appUpdatesEnabled,
                                customColors = customColors,
                                isDark = isDark,
                                onCheckedChange = { viewModel.updateSettings(s.copy(appUpdatesEnabled = it)) }
                            )
                        }
                    }

                    // Quiet hours banner
                    item {
                        QuietHoursBanner(customColors = customColors)
                    }

                    // Footer text
                    item {
                        Text(
                            text = "Changes save automatically",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            color = customColors.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 16.dp)
                        )
                    }
                }
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun NotificationGroupCard(
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
private fun NotificationSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    customColors: CustomColors,
    isDark: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
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
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = customColors.muted
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = if (isDark) Color(0xFF07305B) else Color.White,
                checkedTrackColor = if (isDark) Color(0xFF9CC7FF) else ClinicBlue,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = if (isDark) Color(0xFF151F1B) else Color.White,
                uncheckedTrackColor = customColors.surfaceSunken,
                uncheckedBorderColor = customColors.outlineStrong
            )
        )
    }
}

@Composable
private fun NotificationActionRow(
    icon: ImageVector,
    title: String,
    value: String,
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

        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = customColors.ink,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = customColors.muted
        )
    }
}

@Composable
private fun NotificationDivider(customColors: CustomColors) {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = customColors.outline
    )
}

@Composable
private fun QuietHoursBanner(customColors: CustomColors) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = customColors.softGrey
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = customColors.textGrey,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quiet hours",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = customColors.textGrey
                )
                Text(
                    text = "Non-urgent alerts are held between 10 PM and 7 AM",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    color = customColors.textGrey.copy(alpha = 0.85f)
                )
            }
        }
    }
}

private fun formatTimeForDisplay(timeStr: String): String {
    val parts = timeStr.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val isPm = hour >= 12
    val hour12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val amPm = if (isPm) "PM" else "AM"
    return String.format(Locale.getDefault(), "%02d:%02d %s", hour12, minute, amPm)
}
