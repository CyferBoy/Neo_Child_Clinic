package com.neochildclinic.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.constants.Constants
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDropdownPicker(
    label: String,
    currentDate: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofPattern(Constants.DATE_FORMAT, Locale.ENGLISH) }
    val initialMillis = remember(currentDate) {
        if (currentDate.isEmpty()) null
        else try {
            LocalDate.parse(currentDate, formatter)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) { null }
    }

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
        OutlinedCard(
            onClick = { showPicker = true },
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
            )
        ) {
            Row(
                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentDate.ifEmpty { "Select date" },
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
    }

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        onDateSelected(date.format(formatter))
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

/**
 * Material 3 Outlined Exposed Dropdown Menu over an arbitrary item type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SelectDropdown(
    items: List<T>,
    selected: T?,
    onItemSelected: (T) -> Unit,
    itemLabel: (T) -> String,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String = ""
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selected?.let(itemLabel) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth(),
            isError = isError,
            supportingText = if (isError) {
                { Text(errorText) }
            } else null
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * UI state for the Available Slot dropdown, driven by GetAvailableSlotsUseCase's result -
 * kept separate from the domain-level DoctorAvailabilityResult so screens can also express
 * "nothing selected yet" and "failed to load" without polluting the domain model.
 */
sealed class SlotsUiState {
    object Idle : SlotsUiState()
    object Loading : SlotsUiState()
    object NoScheduleConfigured : SlotsUiState()
    data class FullDayUnavailable(val reason: String?) : SlotsUiState()
    data class Loaded(val slots: List<com.neochildclinic.domain.model.AvailableSlot>) : SlotsUiState()
    data class Error(val message: String) : SlotsUiState()
}

/** Runs GetAvailableSlotsUseCase and maps its result into [SlotsUiState] for display. */
suspend fun com.neochildclinic.domain.usecase.doctor.GetAvailableSlotsUseCase.loadUiState(
    doctorId: String,
    date: String
): SlotsUiState = try {
    when (val result = this(doctorId, date)) {
        is com.neochildclinic.domain.model.DoctorAvailabilityResult.Available -> SlotsUiState.Loaded(result.slots)
        is com.neochildclinic.domain.model.DoctorAvailabilityResult.FullDayUnavailable -> SlotsUiState.FullDayUnavailable(result.reason)
        com.neochildclinic.domain.model.DoctorAvailabilityResult.NoScheduleConfigured -> SlotsUiState.NoScheduleConfigured
    }
} catch (e: Exception) {
    SlotsUiState.Error(e.message ?: "Unable to load availability")
}

/**
 * Material 3 Outlined Exposed Dropdown Menu for the doctor's Available Slot (req. 1/2/3).
 * Purely presentational - the caller (ViewModel) owns loading the [SlotsUiState] via
 * GetAvailableSlotsUseCase and re-loading it whenever doctor/date change (req. 22).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailableSlotDropdown(
    state: SlotsUiState,
    selectedSlot: com.neochildclinic.domain.model.AvailableSlot?,
    onSlotSelected: (com.neochildclinic.domain.model.AvailableSlot) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Available Slot",
    isError: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val isLoading = state is SlotsUiState.Loading
    val slots = (state as? SlotsUiState.Loaded)?.slots.orEmpty()

    val statusText: String? = when (state) {
        SlotsUiState.Idle -> "Select a doctor and date to see available slots"
        SlotsUiState.Loading -> null
        SlotsUiState.NoScheduleConfigured -> "No weekly schedule configured for this doctor"
        is SlotsUiState.FullDayUnavailable -> "Doctor unavailable for the entire day" + (state.reason?.let { " ($it)" } ?: "")
        is SlotsUiState.Error -> state.message
        is SlotsUiState.Loaded -> if (slots.isEmpty()) "No slots available for this doctor on this date" else null
    }

    ExposedDropdownMenuBox(
        expanded = expanded && slots.isNotEmpty(),
        onExpandedChange = { if (slots.isNotEmpty()) expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedSlot?.label ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = !isLoading,
            label = { Text(label) },
            leadingIcon = if (isLoading) {
                { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
            } else null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth(),
            isError = isError,
            supportingText = {
                when {
                    isError -> Text("Available slot selection is mandatory")
                    statusText != null -> Text(statusText)
                }
            }
        )

        ExposedDropdownMenu(
            expanded = expanded && slots.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            slots.forEach { slot ->
                DropdownMenuItem(
                    text = { Text(slot.label) },
                    onClick = {
                        onSlotSelected(slot)
                        expanded = false
                    }
                )
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
) = SelectDropdown(
    items = doctors,
    selected = selectedDoctor,
    onItemSelected = onDoctorSelected,
    itemLabel = { it.displayName },
    label = "Select Doctor",
    modifier = modifier,
    isError = isError,
    errorText = "Doctor selection is mandatory"
)
