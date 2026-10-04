package com.neochildclinic.feature.statistics.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Reminder
import com.neochildclinic.feature.inventory.domain.repository.InventoryRepository
import com.neochildclinic.feature.patient.domain.repository.PatientRepository
import com.neochildclinic.feature.reminder.domain.repository.ReminderRepository
import com.neochildclinic.feature.sync.domain.RefreshDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class VaccineDetailEntry(
    val reminder: Reminder,
    val patient: Patient?,
    val brandName: String
)

data class VaccineDetailUiState(
    val entries: List<VaccineDetailEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** Display name for the drilled-into brand; null when the whole vaccine type is shown. */
    val brandName: String? = null
)

@HiltViewModel
class VaccineDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reminderRepository: ReminderRepository,
    private val inventoryRepository: InventoryRepository,
    patientRepository: PatientRepository,
    private val refreshDataUseCase: RefreshDataUseCase
) : ViewModel() {

    private val type: String = savedStateHandle["type"] ?: ""

    // Null/blank means the user tapped a vaccine TYPE, so every upcoming reminder of that
    // type is listed. Non-blank is the vaccine catalog id of the tapped BRAND.
    private val vaccineId: String? = savedStateHandle.get<String>("vaccineId")?.ifBlank { null }

    private val _isRefreshing = MutableStateFlow(false)
    private val _uiState = MutableStateFlow(VaccineDetailUiState())

    val uiState: StateFlow<VaccineDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Selection and brand-name lookup both run in SQL (DueReminderDao), so this no
            // longer downloads every reminder and filters in Kotlin. The Upcoming section
            // counts the same rows through the same predicate, so a card reading "12" opens
            // exactly 12 entries here - including the is_deleted = 0 rule, which soft-deleted
            // reminders would otherwise slip past.
            val selectedFlow = vaccineId
                ?.let { reminderRepository.getUpcomingVaccinationsByVaccineId(it) }
                ?: reminderRepository.getUpcomingVaccinationsByType(type)

            combine(
                selectedFlow,
                patientRepository.allPatients,
                inventoryRepository.getInventoryItems()
            ) { reminders, patients, inventory ->
                val patientMap = patients.associateBy { it.id }

                // Resolve the brand's display name from the catalog by its stable id, so the
                // header shows the same name the Upcoming card showed. Not used for
                // filtering - the query already selected the rows.
                val brandName = vaccineId?.let { sid ->
                    inventory.firstOrNull { it.id == sid }?.brandName
                }

                // A reminder can list the same vaccine more than once; distinctBy keeps one
                // row per vaccine per reminder so the detail count still matches the card.
                val entries = reminders
                    .flatMap { reminder ->
                        reminder.vaccineName.split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .map { PatientUtils.cleanVaccineName(it) }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .map { name -> VaccineDetailEntry(reminder, patientMap[reminder.patientId], name) }
                    }
                    .let { rows ->
                        val brand = brandName
                        if (brand == null) rows
                        else rows.filter { it.brandName.equals(brand, ignoreCase = true) }
                    }
                    .sortedBy { it.reminder.dueDate }

                VaccineDetailUiState(entries = entries, isLoading = false, brandName = brandName)
            }.flowOn(Dispatchers.Default)
                .catch { e ->
                    if (e is CancellationException) throw e
                    _uiState.value = VaccineDetailUiState(entries = emptyList(), isLoading = false)
                }
                .collect { _uiState.value = it }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                refreshDataUseCase()
            } catch (_: Exception) {
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}