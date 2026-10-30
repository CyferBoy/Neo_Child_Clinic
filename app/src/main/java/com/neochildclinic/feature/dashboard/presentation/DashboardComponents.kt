package com.neochildclinic.feature.dashboard.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Inventory2
import com.neochildclinic.core.common.CurrencyUtils
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.R
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.domain.model.Patient

@Composable
fun DashboardBrandBar(isOnline: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.card, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Clinic Logo",
                    modifier = Modifier.size(38.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.md))
                Column {
                    Text(
                        text = "Neo Child Clinic",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Offering nurturing care...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Online / Offline Status Chip
            val customColors = LocalCustomColors.current
            Surface(
                shape = PillShape,
                color = if (isOnline) customColors.successContainer else customColors.softGrey
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                color = if (isOnline) Color(0xFF067647) else Color(0xFF475467),
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = if (isOnline) "Online" else "Offline",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isOnline) customColors.textSuccess else customColors.textGrey
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardPatientCard(
    modifier: Modifier,
    count: Int,
    onPatientList: () -> Unit,
    onAddPatient: () -> Unit
) {
    val customColors = LocalCustomColors.current
    Box(
        modifier = modifier
            .clip(CardShape)
            .background(customColors.softBlue)
            .clickable { onPatientList() }
            .padding(Spacing.card)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = "Patient List",
                    tint = customColors.textBlue,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column {
                Text(
                    text = "Patient List",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = customColors.textBlue
                )
                Text(
                    text = count.toString(),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = customColors.textBlue,
                    letterSpacing = (-0.035).sp,
                    lineHeight = 40.sp
                )
                Text(
                    text = "patient records",
                    style = MaterialTheme.typography.bodySmall,
                    color = customColors.textBlue.copy(alpha = 0.8f)
                )
            }

            // Quick add pill button and trailing chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.clickable { onAddPatient() },
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add patient",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Add",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                val isDark = isSystemInDarkTheme()
                val circleBg = if (isDark) Color(0xFF151F1B).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(circleBg, CircleShape)
                        .clickable { onPatientList() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open patient list",
                        tint = customColors.textBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardStatisticsCard(
    modifier: Modifier = Modifier,
    revenue: Double,
    growthPercent: Double?,
    onClick: () -> Unit
) {
    val customColors = LocalCustomColors.current

    Box(
        modifier = modifier
            .clip(CardShape)
            .background(customColors.softPurple)
            .clickable { onClick() }
            .padding(Spacing.card)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = "Statistics",
                tint = customColors.textPurple,
                modifier = Modifier.size(24.dp)
            )

            Column {
                Text(
                    text = "Statistics",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = customColors.textPurple
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = CurrencyUtils.formatInr(revenue),
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = customColors.textPurple,
                        letterSpacing = (-0.02).sp,
                        lineHeight = 26.sp
                    )

                    if (growthPercent != null) {
                        val isPositive = growthPercent >= 0
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = if (isPositive) "Growth" else "Decline",
                                tint = customColors.textPurple,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "${kotlin.math.abs(growthPercent.roundToInt())}%",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = customColors.textPurple
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "revenue this month",
                    fontSize = 9.5.sp,
                    color = customColors.textPurple.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun DashboardInventoryCard(
    modifier: Modifier = Modifier,
    lowStockCount: Int,
    outOfStockCount: Int,
    onInventory: () -> Unit
) {
    val customColors = LocalCustomColors.current
    val isDark = isSystemInDarkTheme()
    val outOfStockColor = if (isDark) Color(0xFFFFB4AB) else Color(0xFFB42318)
    val circleBg = if (isDark) Color(0xFF151F1B).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f)

    Box(
        modifier = modifier
            .clip(CardShape)
            .background(customColors.softOrange)
            .clickable { onInventory() }
            .padding(Spacing.card)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.Default.Inventory2,
                contentDescription = "Inventory",
                tint = customColors.textOrange,
                modifier = Modifier.size(24.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Inventory",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = customColors.textOrange
                )

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = lowStockCount.toString(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = customColors.textOrange,
                        lineHeight = 30.sp
                    )
                    Text(
                        text = "low stock",
                        fontSize = 10.sp,
                        color = customColors.textOrange.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = outOfStockCount.toString(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = outOfStockColor,
                        lineHeight = 30.sp
                    )
                    Text(
                        text = "out of stock",
                        fontSize = 10.sp,
                        color = customColors.textOrange.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = customColors.softPink,
                    modifier = Modifier
                        .height(24.dp)
                        .width(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Review",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = customColors.textPink
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(circleBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Review Inventory",
                        tint = customColors.textOrange,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardMainGrid(
    isWideScreen: Boolean,
    uiState: DashboardUiState,
    onPatientList: () -> Unit,
    onAddPatient: () -> Unit,
    onTodayPatients: () -> Unit,
    dashboardAddConsultation: (Patient) -> Unit,
    dashboardAddVaccination: (Patient, String) -> Unit,
    onInventory: () -> Unit,
    onStatistics: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Column 1: Patient List (196dp) and Statistics (138dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            DashboardPatientCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(196.dp),
                count = uiState.patientCount,
                onPatientList = onPatientList,
                onAddPatient = onAddPatient
            )
            DashboardStatisticsCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(138.dp),
                revenue = uiState.currentMonthRevenue,
                growthPercent = uiState.revenueGrowthPercent,
                onClick = onStatistics
            )
        }

        // Column 2: Today's Patients (150dp) and Inventory (184dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            TodayPatientsCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                consultations = uiState.todayConsultations,
                vaccinations = uiState.todayVaccinations,
                patients = uiState.patients,
                onTodayPatients = onTodayPatients,
                onAddConsultation = dashboardAddConsultation,
                onAddVaccination = dashboardAddVaccination
            )
            DashboardInventoryCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(184.dp),
                lowStockCount = uiState.lowStockCount,
                outOfStockCount = uiState.outOfStockCount,
                onInventory = onInventory
            )
        }
    }
}

@Composable
fun DashboardSmallActionsRow(
    uiState: DashboardUiState,
    onBorrowed: () -> Unit,
    onDue: () -> Unit,
    onWaste: () -> Unit
) {
    val customColors = LocalCustomColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        DashboardCardSmall(
            title = "Borrowed",
            icon = Icons.Default.SwapHoriz,
            containerColor = customColors.softGreen,
            contentColor = customColors.textGreen,
            badge = if (uiState.borrowedCount > 0) uiState.borrowedCount.toString() else null,
            modifier = Modifier.weight(1f),
            onClick = onBorrowed
        )
        DashboardCardSmall(
            title = "Due",
            icon = Icons.Default.CalendarToday,
            containerColor = customColors.warningContainer,
            contentColor = customColors.textWarning,
            badge = if (uiState.dueTodayCount > 0) uiState.dueTodayCount.toString() else null,
            modifier = Modifier.weight(1f),
            onClick = onDue
        )
        DashboardCardSmall(
            title = "Waste",
            icon = Icons.Default.DeleteOutline,
            containerColor = customColors.softGrey,
            contentColor = customColors.textGrey,
            badge = if (uiState.wasteCount > 0) uiState.wasteCount.toString() else null,
            modifier = Modifier.weight(1f),
            onClick = onWaste
        )
    }
}

@Composable
fun DashboardQuickActionsRow(
    onAddPatient: () -> Unit,
    onVaccinate: () -> Unit,
    onStock: () -> Unit,
    onExpense: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Quick actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = Spacing.sm)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            QuickActionButton(
                icon = Icons.Default.PersonAdd,
                label = "Patient",
                modifier = Modifier.weight(1f),
                onClick = onAddPatient
            )
            QuickActionButton(
                icon = Icons.Default.MedicalServices,
                label = "Vaccinate",
                modifier = Modifier.weight(1f),
                onClick = onVaccinate
            )
            QuickActionButton(
                icon = Icons.Default.Inventory2,
                label = "Stock",
                modifier = Modifier.weight(1f),
                onClick = onStock
            )
            QuickActionButton(
                icon = Icons.Default.ReceiptLong,
                label = "Expense",
                modifier = Modifier.weight(1f),
                onClick = onExpense
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(70.dp)
            .clickable { onClick() },
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DashboardFooter(
    isOnline: Boolean,
    pendingSyncCount: Int = 0,
    appVersion: String
) {
    val statusText = if (pendingSyncCount > 0) {
        "$pendingSyncCount remains to sync"
    } else {
        "Synced with cloud"
    }

    val displayText = if (isOnline) {
        "Online · $statusText"
    } else {
        if (pendingSyncCount > 0) "Offline · $statusText" else "Offline · Local storage active"
    }

    val dotColor = when {
        !isOnline -> Color(0xFF5F6D68)
        pendingSyncCount > 0 -> Color(0xFF8A5300)
        else -> Color(0xFF067647)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = dotColor,
                        shape = CircleShape
                    )
            )
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "v$appVersion",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
