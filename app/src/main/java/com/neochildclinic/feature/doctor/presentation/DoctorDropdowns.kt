package com.neochildclinic.feature.doctor.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.ui.SelectDropdown
import com.neochildclinic.domain.model.AvailableSlot
import com.neochildclinic.domain.model.DoctorAvailabilityResult
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.feature.doctor.domain.GetAvailableSlotsUseCase

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
    data class Loaded(val slots: List<AvailableSlot>) : SlotsUiState()
    data class Error(val message: String) : SlotsUiState()
}

/** Runs GetAvailableSlotsUseCase and maps its result into [SlotsUiState] for display. */
suspend fun GetAvailableSlotsUseCase.loadUiState(
    doctorId: String,
    date: String
): SlotsUiState = try {
    when (val result = this(doctorId, date)) {
        is DoctorAvailabilityResult.Available -> SlotsUiState.Loaded(result.slots)
        is DoctorAvailabilityResult.FullDayUnavailable -> SlotsUiState.FullDayUnavailable(result.reason)
        DoctorAvailabilityResult.NoScheduleConfigured -> SlotsUiState.NoScheduleConfigured
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
    selectedSlot: AvailableSlot?,
    onSlotSelected: (AvailableSlot) -> Unit,
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
    doctors: List<Profile>,
    selectedDoctor: Profile?,
    onDoctorSelected: (Profile) -> Unit,
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
