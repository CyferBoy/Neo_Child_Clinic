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
    // type or mentioning its brands is listed. Non-blank is the vaccine catalog id of the tapped BRAND.
    private val vaccineId: String? = savedStateHandle.get<String>("vaccineId")?.ifBlank { null }

    private val _isRefreshing = MutableStateFlow(false)
    private val _uiState = MutableStateFlow(VaccineDetailUiState())

    val uiState: StateFlow<VaccineDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                reminderRepository.getUpcomingVaccinations(),
                patientRepository.allPatients,
                inventoryRepository.getInventoryItems()
            ) { reminders, patients, inventory ->
                val patientMap = patients.associateBy { it.id }

                val targetBrand = if (vaccineId != null) {
                    inventory.firstOrNull { it.id == vaccineId || it.brandName.equals(vaccineId, ignoreCase = true) }
                } else null
                val brandName = targetBrand?.brandName ?: vaccineId

                val entries: List<VaccineDetailEntry> = if (brandName != null) {
                    // Specific brand drill-down: show ONLY that specific brand name list
                    val brandClean = PatientUtils.cleanVaccineName(brandName).lowercase()
                    reminders.filter { reminder ->
                        val idMatches = reminder.nxtVaccineId?.contains(vaccineId) == true ||
                                (targetBrand != null && reminder.nxtVaccineId?.contains(targetBrand.id) == true)
                        val nameMatches = reminder.vaccineName.split(",")
                            .map { PatientUtils.cleanVaccineName(it.trim()).lowercase() }
                            .any { it == brandClean }
                        idMatches || nameMatches
                    }.map { reminder ->
                        VaccineDetailEntry(reminder, patientMap[reminder.patientId], brandName)
                    }
                } else {
                    // Type drill-down: show ALL records where type OR brand name is mentioned
                    val typeClean = type.trim()
                    val typeBrands = inventory.filter { it.type.equals(typeClean, ignoreCase = true) }
                    val typeBrandIds = typeBrands.map { it.id }.toSet()
                    val typeBrandNames = typeBrands.map { PatientUtils.cleanVaccineName(it.brandName.trim()).lowercase() }
                        .filter { it.isNotBlank() }
                        .toSet()

                    reminders.filter { reminder ->
                        val remType = reminder.type.trim()
                        val typeMatches = remType.equals(typeClean, ignoreCase = true) ||
                                (remType.isBlank() && typeClean.equals("Other", ignoreCase = true))

                        val names = reminder.vaccineName.split(",")
                            .map { PatientUtils.cleanVaccineName(it.trim()) }
                            .filter { it.isNotBlank() }

                        val nameMentionsType = names.any {
                            it.equals(typeClean, ignoreCase = true) || it.contains(typeClean, ignoreCase = true)
                        }
                        val nameMentionsBrand = names.any {
                            typeBrandNames.contains(it.lowercase())
                        }
                        val idMentionsBrand = reminder.nxtVaccineId?.any { it in typeBrandIds } == true

                        typeMatches || nameMentionsType || nameMentionsBrand || idMentionsBrand
                    }.flatMap { reminder ->
                        val patient = patientMap[reminder.patientId]
                        val names = reminder.vaccineName.split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .map { PatientUtils.cleanVaccineName(it) }
                            .filter { it.isNotBlank() }
                            .distinct()

                        if (names.isEmpty()) {
                            // Try resolving brand name from nxtVaccineId if available
                            val idBrands = reminder.nxtVaccineId?.mapNotNull { vid ->
                                inventory.firstOrNull { it.id == vid }?.brandName
                            } ?: emptyList()

                            if (idBrands.isNotEmpty()) {
                                idBrands.distinct().map { VaccineDetailEntry(reminder, patient, it) }
                            } else {
                                listOf(VaccineDetailEntry(reminder, patient, reminder.type.ifBlank { typeClean }))
                            }
                        } else {
                            names.map { name ->
                                VaccineDetailEntry(reminder, patient, name)
                            }
                        }
                    }
                }.sortedBy { it.reminder.dueDate }

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
