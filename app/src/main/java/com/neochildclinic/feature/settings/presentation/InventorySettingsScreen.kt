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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventorySettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val customColors = LocalCustomColors.current

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Inventory Settings",
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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            settings?.let { s ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = Spacing.screen),
                    contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Top Low Stock Threshold Card (116dp, peach background #F8E4D7, matching SVG)
                    item {
                        LowStockThresholdBannerCard(
                            threshold = s.lowStockThreshold,
                            onDecrement = {
                                if (s.lowStockThreshold > 1) {
                                    viewModel.updateSettings(s.copy(lowStockThreshold = s.lowStockThreshold - 1))
                                }
                            },
                            onIncrement = {
                                if (s.lowStockThreshold < 100) {
                                    viewModel.updateSettings(s.copy(lowStockThreshold = s.lowStockThreshold + 1))
                                }
                            }
                        )
                    }

                    // 2. Card 1: Alert Toggles (Low stock alerts, Expiry warnings, Hide expired & empty)
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Row 1: Low stock alerts
                                SettingSwitchRow(
                                    icon = Icons.Default.Notifications,
                                    title = "Low stock alerts",
                                    subtitle = "Show in dashboard and notifications",
                                    checked = s.lowStockEnabled,
                                    onCheckedChange = { viewModel.updateSettings(s.copy(lowStockEnabled = it)) }
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outline,
                                    thickness = 1.dp
                                )

                                // Row 2: Expiry warnings
                                SettingSwitchRow(
                                    icon = Icons.Default.Schedule,
                                    title = "Expiry warnings",
                                    subtitle = "Warn before batches expire",
                                    checked = s.expiryWarningsEnabled,
                                    onCheckedChange = { viewModel.updateSettings(s.copy(expiryWarningsEnabled = it)) }
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outline,
                                    thickness = 1.dp
                                )

                                // Row 3: Hide expired & empty
                                SettingSwitchRow(
                                    icon = Icons.Default.VisibilityOff,
                                    title = "Hide expired & empty",
                                    subtitle = "Hide unusable batches by default",
                                    checked = s.hideExpiredEmpty,
                                    onCheckedChange = { viewModel.updateSettings(s.copy(hideExpiredEmpty = it)) }
                                )
                            }
                        }
                    }

                    // 3. Card 2: Warning Window (64dp height, matching SVG)
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Warn me",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = customColors.textGrey
                                    )
                                    Text(
                                        text = "before expiry",
                                        fontSize = 9.sp,
                                        color = customColors.textGrey
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        val nextDays = when (s.warnDaysBeforeExpiry) {
                                            30 -> 60
                                            60 -> 15
                                            else -> 30
                                        }
                                        viewModel.updateSettings(s.copy(warnDaysBeforeExpiry = nextDays))
                                    }
                                ) {
                                    Text(
                                        text = "${s.warnDaysBeforeExpiry} days",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = customColors.textGrey,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 4. Card 3: Sorting & Display (Sort inventory by & Show stock value)
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Row 1: Sort inventory by
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val nextSort = when (s.sortMethod) {
                                                "Alphabetical" -> "Expiry Date"
                                                "Expiry Date" -> "Quantity"
                                                else -> "Alphabetical"
                                            }
                                            viewModel.updateSettings(s.copy(sortMethod = nextSort))
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Sort inventory by",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = s.sortMethod,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = customColors.textGrey
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outline,
                                    thickness = 1.dp
                                )

                                // Row 2: Show stock value
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Show stock value",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Include value in summary cards",
                                            fontSize = 10.5.sp,
                                            color = customColors.textGrey
                                        )
                                    }

                                    Switch(
                                        checked = s.showStockValueEnabled,
                                        onCheckedChange = { viewModel.updateSettings(s.copy(showStockValueEnabled = it)) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFF16558F)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // 5. Bottom Info Notice (54dp, #EEF0F2 background, 14dp corners)
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFEEF0F2)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFF344054),
                                    modifier = Modifier.size(18.dp)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Text(
                                        text = "Applies to all inventory screens",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF344054)
                                    )
                                    Text(
                                        text = "Alerts are calculated locally from batch quantities",
                                        fontSize = 9.5.sp,
                                        color = Color(0xFF344054).copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                }
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * 116dp Low Stock Threshold Banner Card from inventory_settings_light.svg:
 * Peach background `#F8E4D7`, Box icon in `#5B2A12`, "Low stock threshold",
 * 36sp bold number + "doses", "Warn when a batch has fewer doses", and circular `−` / `+` stepper buttons.
 */
@Composable
private fun LowStockThresholdBannerCard(
    threshold: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF8E4D7),
        border = BorderStroke(1.dp, Color(0xFFE8CCBC))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = Color(0xFF5B2A12),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Low stock threshold",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5B2A12)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$threshold",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5B2A12),
                        letterSpacing = (-1).sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "doses",
                        fontSize = 11.sp,
                        color = Color(0xFF5B2A12).copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Text(
                    text = "Warn when a batch has fewer doses",
                    fontSize = 9.5.sp,
                    color = Color(0xFF5B2A12).copy(alpha = 0.8f)
                )
            }

            // Stepper Circular Buttons: [−] [+]
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Minus button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { onDecrement() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease",
                        tint = Color(0xFF5B2A12),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Plus button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { onIncrement() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase",
                        tint = Color(0xFF5B2A12),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Standard switch row inside settings card:
 * Icon container (34dp `#F0ECE3`), Title, Subtitle, Switch toggle.
 */
@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val customColors = LocalCustomColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = customColors.textGrey
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF16558F)
            )
        )
    }
}
