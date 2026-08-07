package com.neochildclinic.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.constants.Constants
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dropdown menu typically used for item actions like Edit and Delete.
 */
@Composable
fun ActionDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPrint: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onMerge: (() -> Unit)? = null,
    onMarkAsDone: (() -> Unit)? = null,
    onEditRole: (() -> Unit)? = null,
    onAuditLog: (() -> Unit)? = null,
    isAdmin: Boolean = true,
    editText: String = "Edit"
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        if (onMarkAsDone != null) {
            DropdownMenuItem(
                text = { Text("Mark as Done") },
                onClick = {
                    onDismiss()
                    onMarkAsDone()
                },
                leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)) }
            )
            HorizontalDivider()
        }
        if (onAuditLog != null) {
            DropdownMenuItem(
                text = { Text("View Audit Log") },
                onClick = {
                    onDismiss()
                    onAuditLog()
                },
                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
            )
        }
        if (onPrint != null) {
            DropdownMenuItem(
                text = { Text("Print Receipt") },
                onClick = {
                    onDismiss()
                    onPrint()
                },
                leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) }
            )
        }
        if (onDownload != null) {
            DropdownMenuItem(
                text = { Text("Download Receipt") },
                onClick = {
                    onDismiss()
                    onDownload()
                },
                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
            )
        }
        
        if (isAdmin) {
            DropdownMenuItem(
                text = { Text(editText) },
                onClick = {
                    onDismiss()
                    onEdit()
                },
                leadingIcon = { Icon(if (editText == "Edit") Icons.Default.Edit else Icons.Default.LockReset, contentDescription = null) }
            )
        }

        if (onEditRole != null && isAdmin) {
            DropdownMenuItem(
                text = { Text("Change Role") },
                onClick = {
                    onDismiss()
                    onEditRole()
                },
                leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) }
            )
        }
        if (onMerge != null) {
            DropdownMenuItem(
                text = { Text("Merge") },
                onClick = {
                    onDismiss()
                    onMerge()
                },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null) }
            )
        }

        if (isAdmin) {
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    onDismiss()
                    onDelete()
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
            )
        }
    }
}

/**
 * Dropdown-style date picker that allows selecting Day, Month, and Year.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDropdownPicker(
    label: String,
    currentDate: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val calendar = Calendar.getInstance()
    
    // Parse current date or use today
    if (currentDate.isNotEmpty()) {
        try {
            val date = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).parse(currentDate)
            if (date != null) calendar.time = date
        } catch (_: Exception) {}
    }

    val day = calendar[Calendar.DAY_OF_MONTH]
    val month = calendar[Calendar.MONTH]
    val year = calendar[Calendar.YEAR]

    var expandedDay by remember { mutableStateOf(false) }
    var expandedMonth by remember { mutableStateOf(false) }
    var expandedYear by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Day Dropdown
            Box(modifier = Modifier.weight(1f)) {
                OutlinedCard(
                    onClick = { expandedDay = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (expandedDay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentDate.isEmpty()) "Day" else day.toString(), 
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (currentDate.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            Icons.Default.DateRange, 
                            contentDescription = null, 
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                DropdownMenu(
                    expanded = expandedDay, 
                    onDismissRequest = { expandedDay = false },
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                    (1..daysInMonth).forEach { d ->
                        DropdownMenuItem(text = { Text(d.toString()) }, onClick = {
                            calendar[Calendar.DAY_OF_MONTH] = d
                            onDateSelected(SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(calendar.time))
                            expandedDay = false
                        })
                    }
                }
            }

            // Month Dropdown
            Box(modifier = Modifier.weight(1.5f)) {
                val months = SimpleDateFormat("MMM", Locale.ENGLISH).let { fmt ->
                    (0..11).map { m -> 
                        val cal = Calendar.getInstance().apply { set(Calendar.MONTH, m) }
                        fmt.format(cal.time)
                    }
                }
                OutlinedCard(
                    onClick = { expandedMonth = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (expandedMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentDate.isEmpty()) "Month" else months[month], 
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (currentDate.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            Icons.Default.DateRange, 
                            contentDescription = null, 
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                DropdownMenu(expanded = expandedMonth, onDismissRequest = { expandedMonth = false }) {
                    months.forEachIndexed { index, m ->
                        DropdownMenuItem(text = { Text(m) }, onClick = {
                            calendar[Calendar.MONTH] = index
                            onDateSelected(SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(calendar.time))
                            expandedMonth = false
                        })
                    }
                }
            }

            // Year Dropdown
            Box(modifier = Modifier.weight(1.5f)) {
                OutlinedCard(
                    onClick = { expandedYear = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (expandedYear) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentDate.isEmpty()) "Year" else year.toString(), 
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (currentDate.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            Icons.Default.DateRange, 
                            contentDescription = null, 
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                DropdownMenu(
                    expanded = expandedYear, 
                    onDismissRequest = { expandedYear = false },
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    val currentYear = Calendar.getInstance()[Calendar.YEAR]
                    // Create list of years from 10 years in future to 100 years ago.
                    // We reverse the order so the most relevant (current) years are at the TOP.
                    ((currentYear + 10) downTo (currentYear - 100)).forEach { y ->
                        DropdownMenuItem(text = { Text(y.toString()) }, onClick = {
                            calendar[Calendar.YEAR] = y
                            onDateSelected(SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(calendar.time))
                            expandedYear = false
                        })
                    }
                }
            }
        }
    }
}

/**
 * Material 3 Outlined Exposed Dropdown Menu for Doctor selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorDropdown(
    doctors: List<com.neochildclinic.domain.model.Profile>,
    selectedDoctor: com.neochildclinic.domain.model.Profile?,
    onDoctorSelected: (com.neochildclinic.domain.model.Profile) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedDoctor?.displayName ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Select Doctor") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth(),
            isError = isError,
            supportingText = if (isError) {
                { Text("Doctor selection is mandatory") }
            } else null
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            doctors.forEach { doctor ->
                DropdownMenuItem(
                    text = { Text(doctor.displayName) },
                    onClick = {
                        onDoctorSelected(doctor)
                        expanded = false
                    }
                )
            }
        }
    }
}
