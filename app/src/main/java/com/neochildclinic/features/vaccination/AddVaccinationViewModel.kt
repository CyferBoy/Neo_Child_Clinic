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

data class NextVaccinationState(
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
    val nextVaccinations: List<NextVaccinationState> = emptyList(),
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

            // Wait for inventory and doctor data before restoring the edit state.
            uiState.filter { it.inventory.isNotEmpty() }.first()
            if (_uiState.value.allDoctors.isEmpty()) {
                uiState.filter { it.allDoctors.isNotEmpty() }.first()
            }

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

            // Load existing Next Vaccination entries directly from reminders.
            val reminders = reminderRepository.getRemindersByVisitId(vaccinationId)
            val nextStates = reminders.map { reminder ->
                val nextVaccineIds = reminder.nxtVaccineId ?: emptyList()
                val nextVaccines = nextVaccineIds.mapNotNull { id -> inventory.find { it.id == id } }
                NextVaccinationState(
                    type = reminder.type,
                    dueDate = reminder.dueDate,
                    nextVaccines = nextVaccines
                )
            }

            val existingDoctor = _uiState.value.allDoctors.firstOrNull {
                it.employeeId == vaccination.doctorId
            }

            _uiState.update { it.copy(
                existingVaccinationId = vaccinationId,
                givenDate = vaccination.dateGiven,
                selectedDoctor = existingDoctor ?: it.selectedDoctor,
                vaccinesGiven = if (items.isNotEmpty()) items else listOf(VaccineSelectionState()),
                nextVaccinations = nextStates,
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

    fun updateQuantity(rowId: String, quantity: String) {
        val parsed = quantity.toIntOrNull()?.coerceAtLeast(1) ?: return
        _uiState.update { state ->
            state.copy(vaccinesGiven = state.vaccinesGiven.map { row ->
                if (row.id == rowId) row.copy(quantity = parsed) else row
            })
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

    fun addNextVaccination() {
        _uiState.update { it.copy(nextVaccinations = it.nextVaccinations + NextVaccinationState()) }
    }

    fun removeNextVaccination(index: Int) {
        _uiState.update { state ->
            if (index !in state.nextVaccinations.indices) state
            else state.copy(nextVaccinations = state.nextVaccinations.toMutableList().also { it.removeAt(index) })
        }
    }

    fun updateNextVaccinationType(index: Int, type: String) {
        _uiState.update { state ->
            if (index !in state.nextVaccinations.indices) return@update state
            val rows = state.nextVaccinations.toMutableList()
            rows[index] = rows[index].copy(type = type, nextVaccines = emptyList(), typeError = false)
            state.copy(nextVaccinations = rows)
        }
    }

    fun toggleNextVaccinationVaccine(index: Int, vaccine: InventoryItem) {
        _uiState.update { state ->
            if (index !in state.nextVaccinations.indices) return@update state
            val rows = state.nextVaccinations.toMutableList()
            val current = rows[index].nextVaccines
            rows[index] = rows[index].copy(
                nextVaccines = if (current.any { it.id == vaccine.id }) current.filter { it.id != vaccine.id } else current + vaccine
            )
            state.copy(nextVaccinations = rows)
        }
    }

    fun updateNextVaccinationDueDate(index: Int, dueDate: String) {
        _uiState.update { state ->
            if (index !in state.nextVaccinations.indices) return@update state
            val rows = state.nextVaccinations.toMutableList()
            rows[index] = rows[index].copy(dueDate = dueDate)
            state.copy(nextVaccinations = rows)
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

        // Next Vaccination validation: every entered row requires Type and Due Date.
        val nextRows = state.nextVaccinations
        val invalidIndex = nextRows.indexOfFirst { it.type.isBlank() || it.dueDate.isBlank() }
        if (invalidIndex >= 0) {
            val rows = nextRows.toMutableList()
            rows[invalidIndex] = rows[invalidIndex].copy(typeError = rows[invalidIndex].type.isBlank())
            _uiState.update { it.copy(
                errorMessage = "Each Next Vaccination entry requires a Type and Due Date.",
                nextVaccinations = rows
            ) }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"
                val vaccinationId = state.existingVaccinationId ?: UUID.randomUUID().toString()
                val isEdit = state.existingVaccinationId != null

                // Capture the original record before replacing it. This is required for
                // correct inventory reconciliation during Edit.
                val existingVaccination = if (isEdit) {
                    vaccinationRepository.getVaccinationById(vaccinationId)
                } else null

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
                    nextVaccinations = emptyList()
                )

                // 1. Update the existing visit (or create a new one). The same visit ID is
                // always retained during Edit. Finance is handled edit-safely by the service.
                clinicalService.recordVaccination(vaccination, user, isNew = !isEdit)

                // 2. Reconcile inventory. For Edit, restore the exact quantities from the
                // original vaccination first, then deduct the newly selected quantities.
                if (isEdit && existingVaccination != null) {
                    existingVaccination.items.forEach { oldItem ->
                        inventoryRepository.reverseDeduction(
                            batchId = oldItem.batchId,
                            quantity = oldItem.quantity,
                            user = user
                        )
                    }
                }

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

                // 3. Replace the Reminder rows belonging to this visit. Editing must not
                // leave stale Next Vaccination records behind. Consultation Follow-up is
                // completely separate and is not touched here.
                if (isEdit) {
                    reminderRepository.getRemindersByVisitId(vaccinationId).forEach { reminder ->
                        reminderRepository.deleteReminder(reminder, user)
                    }
                }

                // 4. Save the current Next Vaccination entries directly to reminders.
                // Multiple entries may share the same due date; each entry keeps its own
                // Type → Vaccine relationship.
                nextRows.forEach { next ->
                    reminderRepository.saveNextVaccination(
                        patientId = patient.id,
                        originalVisitId = vaccinationId,
                        type = next.type,
                        vaccineNames = next.nextVaccines.map { it.brandName },
                        nxtVaccineId = next.nextVaccines.map { it.id },
                        dueDate = next.dueDate,
                        notes = "Scheduled during visit on ${state.givenDate}",
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
