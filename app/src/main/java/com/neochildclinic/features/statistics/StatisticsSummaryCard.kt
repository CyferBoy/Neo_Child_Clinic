package com.neochildclinic.features.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
import com.neochildclinic.core.designsystem.LocalCustomColors
import com.neochildclinic.core.designsystem.SuccessGreen
import com.neochildclinic.core.designsystem.ErrorRed
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSection(
    availableYears: List<String>,
    filterMode: String,
    fyQuarter: Int,
    selectedMonth: Int,
    onFilterModeChange: (String) -> Unit,
    onQuarterChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val quarterEnabled = filterMode != "Overall"
    val monthEnabled = filterMode != "Overall" && fyQuarter != 0

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        var yearExpanded by remember { mutableStateOf(false) }
        val currentFY = StatisticsUtils.displayFilterMode(filterMode)
        ExposedDropdownMenuBox(
            expanded = yearExpanded,
            onExpandedChange = { yearExpanded = it },
            modifier = Modifier.weight(1.3f)
        ) {
            OutlinedTextField(
                value = "Financial Year  $currentFY",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.menuAnchor(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )
            ExposedDropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Overall") },
                    onClick = { onFilterModeChange("Overall"); yearExpanded = false }
                )
                availableYears.forEach { year ->
                    DropdownMenuItem(
                        text = { Text(year) },
                        onClick = { onFilterModeChange("FY ${year.takeLast(5)}"); yearExpanded = false }
                    )
                }
            }
        }

        var qExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = qExpanded && quarterEnabled,
            onExpandedChange = { if (quarterEnabled) qExpanded = it },
            modifier = Modifier.weight(0.9f)
        ) {
            OutlinedTextField(
                value = if (fyQuarter == 0) "Quarter  All" else "Quarter  Q$fyQuarter",
                onValueChange = {},
                readOnly = true,
                enabled = quarterEnabled,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qExpanded && quarterEnabled) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                    disabledContainerColor = disabledContainerColor,
                    disabledTextColor = disabledTextColor
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.menuAnchor(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )
            if (quarterEnabled) {
                ExposedDropdownMenu(expanded = qExpanded, onDismissRequest = { qExpanded = false }) {
                    DropdownMenuItem(text = { Text("All") }, onClick = { onQuarterChange(0); qExpanded = false })
                    (1..4).forEach { q ->
                        DropdownMenuItem(text = { Text("Q$q") }, onClick = { onQuarterChange(q); qExpanded = false })
                    }
                }
            }
        }

        var mExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = mExpanded && monthEnabled,
            onExpandedChange = { if (monthEnabled) mExpanded = it },
            modifier = Modifier.weight(0.8f)
        ) {
            OutlinedTextField(
                value = if (selectedMonth == -1) "Month  All" else "Month  ${StatisticsUtils.monthNames[selectedMonth]}",
                onValueChange = {},
                readOnly = true,
                enabled = monthEnabled,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mExpanded && monthEnabled) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                    disabledContainerColor = disabledContainerColor,
                    disabledTextColor = disabledTextColor
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.menuAnchor(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )
            if (monthEnabled) {
                ExposedDropdownMenu(expanded = mExpanded, onDismissRequest = { mExpanded = false }) {
                    DropdownMenuItem(text = { Text("All") }, onClick = { onMonthChange(-1); mExpanded = false })
                    val months = if (fyQuarter == 0) (0..11).toList() else StatisticsUtils.fyQuarters[fyQuarter - 1].second
                    months.forEach { mIdx ->
                        DropdownMenuItem(text = { Text(StatisticsUtils.monthNames[mIdx]) }, onClick = { onMonthChange(mIdx); mExpanded = false })
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    iconBackground: Color,
    growthPercentage: Double? = null
) {
    val customColors = LocalCustomColors.current
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(customColors.bgOffWhite)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            if (growthPercentage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isPositive = growthPercentage >= 0
                    val color = if (isPositive) SuccessGreen else ErrorRed
                    val arrowIcon = if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                    
                    Icon(
                        imageVector = arrowIcon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f%%", kotlin.math.abs(growthPercentage)),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "vs previous period",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
