package com.neochildclinic.features.vaccination

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.constants.Constants
import com.neochildclinic.core.utils.InventoryUtils
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.domain.model.*
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.VaccinationRepository
import com.neochildclinic.domain.service.ClinicalVaccinationService
import io.github.jan.supabase.auth.Auth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class VaccineSelectionState(
    val id: String = UUID.randomUUID().toString(),
    val selectedVaccine: InventoryItem? = null,
    val selectedBatch: VaccineBatchEntity? = null,
    val quantity: Int = 1
)

data class FollowUpSelectionState(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "",
    val nextVaccines: List<InventoryItem> = emptyList(),
    val dueDate: String = "",
    val typeError: Boolean = false
)

data class AddVaccinationUiState(
    val patient: Patient? = null,
    val isLoading: Boolean = false,
    val inventory: List<InventoryItem> = emptyList(),
    val availableDueTypes: List<String> = emptyList(),
    val allDoctors: List<Profile> = emptyList(),
    val selectedDoctor: Profile? = null,
    val doctorError: Boolean = false,
    val givenDate: String = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date()),
    val vaccinesGiven: List<VaccineSelectionState> = listOf(VaccineSelectionState()),
    val followUps: List<FollowUpSelectionState> = emptyList(),
    val cashAmount: String = "0",
    val onlineAmount: String = "0",
    val totalAmount: Double = 0.0,
    val existingVaccinationId: String? = null,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AddVaccinationViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val inventoryRepository: InventoryRepository,
    private val vaccinationRepository: VaccinationRepository,
    private val reminderRepository: ReminderRepository,
    private val profileRepository: com.neochildclinic.domain.repository.ProfileRepository,
    private val clinicalService: ClinicalVaccinationService,
    private val auth: Auth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddVaccinationUiState())
    val uiState: StateFlow<AddVaccinationUiState> = _uiState.asStateFlow()

    init {
        fetchInventory()
        fetchDoctors()
    }

    fun loadPatient(patientId: String) {
        if (patientId.isBlank()) return
        viewModelScope.launch {
            val patient = patientRepository.getPatientById(patientId)
            _uiState.update { it.copy(patient = patient) }
        }
    }

    fun loadVaccination(vaccinationId: String?) {
        if (vaccinationId.isNullOrBlank()) return
        viewModelScope.launch {
            val vaccination = vaccinationRepository.getVaccinationById(vaccinationId) ?: return@launch
            loadPatient(vaccination.patientId)

            // Wait for inventory if not loaded
            uiState.filter { it.inventory.isNotEmpty() }.first()

            val inventory = _uiState.value.inventory
            val items = vaccination.items.map { item ->
                val vaccine = inventory.find { it.id == item.vaccineId }
                val batch = vaccine?.batches?.find { it.batchId == item.batchId }
                VaccineSelectionState(
                    selectedVaccine = vaccine,
                    selectedBatch = batch,
                    quantity = item.quantity
                )
            }

            // Load existing follow-ups
            val reminders = reminderRepository.getRemindersByVisitId(vaccinationId)
            val followUpStates = reminders.map { reminder ->
                val nextVaccineIds = reminder.nxtVaccineId ?: emptyList()
                val nextVaccines = nextVaccineIds.mapNotNull { id -> inventory.find { it.id == id } }
                
                FollowUpSelectionState(
                    type = reminder.type,
                    dueDate = reminder.dueDate,
                    nextVaccines = nextVaccines
                )
            }

            _uiState.update { it.copy(
                existingVaccinationId = vaccinationId,
                givenDate = vaccination.dateGiven,
                vaccinesGiven = if (items.isNotEmpty()) items else listOf(VaccineSelectionState()),
                followUps = followUpStates,
                cashAmount = vaccination.cashAmount.toInt().toString(),
                onlineAmount = vaccination.onlineAmount.toInt().toString(),
                totalAmount = vaccination.totalPaid
            ) }
        }
    }

    fun setInitialVaccine(vaccineName: String?) {
        if (vaccineName.isNullOrBlank()) return
        viewModelScope.launch {
            // Wait for inventory
            uiState.filter { it.inventory.isNotEmpty() }.first()
            
            val inventory = _uiState.value.inventory
            val vaccine = inventory.find { it.brandName.equals(vaccineName, ignoreCase = true) }
            
            if (vaccine != null) {
                val rowId = _uiState.value.vaccinesGiven.firstOrNull()?.id ?: UUID.randomUUID().toString()
                if (_uiState.value.vaccinesGiven.isEmpty()) {
                    _uiState.update { it.copy(vaccinesGiven = listOf(VaccineSelectionState(id = rowId))) }
                }
                selectVaccine(rowId, vaccine)
            }
        }
    }

    private fun fetchInventory() {
        inventoryRepository.getInventoryItems().onEach { items ->
            val types = items.map { it.type }.filter { it.isNotBlank() }.distinct().sorted()
            _uiState.update { it.copy(
                inventory = items,
                availableDueTypes = if (types.isEmpty()) Constants.DUE_VACCINATION_TYPES else types
            ) }
        }.launchIn(viewModelScope)
    }

    private fun fetchDoctors() {
        profileRepository.allProfiles.onEach { profiles ->
            val doctors = profiles.filter { it.role == UserRole.doctor && it.isActive }
                .sortedBy { it.displayName }
            
            val currentUserId = auth.currentSessionOrNull()?.user?.id
            val currentUserProfile = profiles.find { it.id == currentUserId }
            val defaultDoctor = if (currentUserProfile?.role == UserRole.doctor) currentUserProfile else null

            _uiState.update { it.copy(
                allDoctors = doctors,
                selectedDoctor = if (it.selectedDoctor == null) defaultDoctor else it.selectedDoctor
            ) }
        }.launchIn(viewModelScope)
    }

    fun selectDoctor(doctor: Profile) {
        _uiState.update { it.copy(selectedDoctor = doctor, doctorError = false) }
    }

    fun updateGivenDate(date: String) {
        _uiState.update { it.copy(givenDate = date) }
    }

    fun addVaccineRow() {
        _uiState.update { it.copy(vaccinesGiven = it.vaccinesGiven + VaccineSelectionState()) }
    }

    fun removeVaccineRow(id: String) {
        if (_uiState.value.vaccinesGiven.size > 1) {
            _uiState.update { it.copy(vaccinesGiven = it.vaccinesGiven.filter { row -> row.id != id }) }
        }
    }

    fun selectVaccine(rowId: String, vaccine: InventoryItem) {
        val bestBatch = vaccine.batches
            .filter { it.remainingQuantity > 0 && !InventoryUtils.isExpired(it.expiryDate) }
            .minByOrNull { PatientUtils.parseDate(it.expiryDate) ?: Date(Long.MAX_VALUE) }

        _uiState.update { state ->
            val updated = state.vaccinesGiven.map { row ->
                if (row.id == rowId) row.copy(selectedVaccine = vaccine, selectedBatch = bestBatch)
                else row
            }
            state.copy(vaccinesGiven = updated)
        }
    }

    fun selectBatch(rowId: String, batch: VaccineBatchEntity) {
        _uiState.update { state ->
            val updated = state.vaccinesGiven.map { row ->
                if (row.id == rowId) row.copy(selectedBatch = batch)
                else row
            }
            state.copy(vaccinesGiven = updated)
        }
    }

    fun updateCash(amount: String) {
        val cash = amount.toDoubleOrNull() ?: 0.0
        val online = _uiState.value.onlineAmount.toDoubleOrNull() ?: 0.0
        _uiState.update { it.copy(cashAmount = amount, totalAmount = cash + online) }
    }

    fun updateOnline(amount: String) {
        val online = amount.toDoubleOrNull() ?: 0.0
        val cash = _uiState.value.cashAmount.toDoubleOrNull() ?: 0.0
        _uiState.update { it.copy(onlineAmount = amount, totalAmount = cash + online) }
    }

    fun addFollowUpRow() {
        _uiState.update { it.copy(followUps = it.followUps + FollowUpSelectionState()) }
    }

    fun removeFollowUpRow(id: String) {
        _uiState.update { it.copy(followUps = it.followUps.filter { row -> row.id != id }) }
    }

    fun updateFollowUpType(rowId: String, type: String) {
        _uiState.update { state ->
            state.copy(followUps = state.followUps.map { row ->
                if (row.id == rowId) row.copy(type = type, typeError = false) else row
            })
        }
    }

    fun toggleFollowUpVaccine(rowId: String, vaccine: InventoryItem) {
        _uiState.update { state ->
            state.copy(followUps = state.followUps.map { row ->
                if (row.id != rowId) return@map row
                val alreadySelected = row.nextVaccines.any { it.id == vaccine.id }
                val updatedVaccines = if (alreadySelected) {
                    row.nextVaccines.filter { it.id != vaccine.id }
                } else {
                    row.nextVaccines + vaccine
                }
                row.copy(nextVaccines = updatedVaccines)
            })
        }
    }

    fun updateFollowUpDueDate(rowId: String, dueDate: String) {
        _uiState.update { state ->
            state.copy(followUps = state.followUps.map { row ->
                if (row.id == rowId) row.copy(dueDate = dueDate) else row
            })
        }
    }

    fun saveVaccination() {
        val state = _uiState.value
        val patient = state.patient ?: return
        
        if (state.selectedDoctor == null) {
            _uiState.update { it.copy(doctorError = true, errorMessage = "Please select a doctor.") }
            return
        }

        if (state.vaccinesGiven.any { it.selectedVaccine == null || it.selectedBatch == null }) {
            _uiState.update { it.copy(errorMessage = "Please select vaccine and batch for all rows.") }
            return
        }

        // Next Vaccination validation: Type is mandatory for any row the user has
        // started filling in (i.e. has a due date). Vaccine selection stays optional.
        val invalidFollowUps = state.followUps.filter { it.dueDate.isNotBlank() && it.type.isBlank() }
        if (invalidFollowUps.isNotEmpty()) {
            _uiState.update { s ->
                s.copy(
                    errorMessage = "Please select a Type for all Next Vaccination entries.",
                    followUps = s.followUps.map { row ->
                        if (row.dueDate.isNotBlank() && row.type.isBlank()) row.copy(typeError = true) else row
                    }
                )
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"
                val vaccinationId = state.existingVaccinationId ?: UUID.randomUUID().toString()

                val items = state.vaccinesGiven.map { selection ->
                    VaccinationItem(
                        id = UUID.randomUUID().toString(),
                        vaccinationId = vaccinationId,
                        vaccineId = selection.selectedVaccine!!.id,
                        vaccineName = selection.selectedVaccine.brandName,
                        batchId = selection.selectedBatch!!.batchId,
                        batchNumber = selection.selectedBatch.batchNumber,
                        expiryDate = selection.selectedBatch.expiryDate,
                        quantity = selection.quantity,
                        mrp = selection.selectedBatch.sellingPrice,
                        netRate = selection.selectedBatch.purchaseCost
                    )
                }

                val followUpRequirements = state.followUps.filter { it.dueDate.isNotBlank() && it.type.isNotBlank() }
                    .flatMap { row ->
                        val vaccines = row.nextVaccines
                        if (vaccines.isEmpty()) {
                            listOf(FollowUpRequirement(nextVaccineId = "", nextVaccineName = "", dueDate = row.dueDate))
                        } else {
                            vaccines.map { v -> FollowUpRequirement(nextVaccineId = v.id, nextVaccineName = v.brandName, dueDate = row.dueDate) }
                        }
                    }

                val vaccination = Vaccination(
                    id = vaccinationId,
                    patientId = patient.id,
                    patientName = patient.name,
                    patientClinicId = patient.patientClinicId,
                    dateGiven = state.givenDate,
                    cashAmount = state.cashAmount.toDoubleOrNull() ?: 0.0,
                    onlineAmount = state.onlineAmount.toDoubleOrNull() ?: 0.0,
                    totalPaid = state.totalAmount,
                    doctorId = state.selectedDoctor.employeeId ?: "",
                    performedBy = state.selectedDoctor.displayName,
                    items = items,
                    followUps = followUpRequirements
                )

                // 1. Record clinical visit and satisfy reminders
                clinicalService.recordVaccination(vaccination, user)

                // 2. Process Inventory deductions
                state.vaccinesGiven.forEach { selection ->
                    inventoryRepository.deductStockFromBatch(
                        batchId = selection.selectedBatch!!.batchId,
                        quantity = selection.quantity,
                        user = user,
                        transactionType = InventoryTransactionType.VACCINATION,
                        visitId = vaccinationId,
                        patientId = patient.id
                    )
                }

                // 3. Schedule next vaccinations (grouped by due date + Type; Type is
                // mandatory per Due Vaccination record, vaccine selection is optional)
                val followUpsToSchedule = state.followUps.filter { it.dueDate.isNotBlank() && it.type.isNotBlank() }
                val followUpsGrouped = followUpsToSchedule.groupBy { it.dueDate to it.type }

                followUpsGrouped.forEach { (key, rows) ->
                    val (date, type) = key
                    val vaccines = rows.flatMap { it.nextVaccines }.distinctBy { it.id }
                    reminderRepository.scheduleFollowUp(
                        patientId = patient.id,
                        originalVisitId = vaccinationId,
                        type = type,
                        vaccineNames = vaccines.map { it.brandName },
                        nxtVaccineId = vaccines.map { it.id },
                        dueDate = date,
                        notes = "Scheduled during visit on ${state.givenDate}",
                        priority = "NORMAL",
                        reminderEnabled = true,
                        performedBy = user
                    )
                }

                _uiState.update { it.copy(isLoading = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }
}
