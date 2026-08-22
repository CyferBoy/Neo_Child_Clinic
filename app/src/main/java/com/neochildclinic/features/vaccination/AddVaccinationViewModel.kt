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
import com.neochildclinic.domain.service.VaccinationEditEngine
import io.github.jan.supabase.auth.Auth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    val isVaccinationLoading: Boolean = false,
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
    private val vaccinationEditEngine: VaccinationEditEngine,
    private val auth: Auth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddVaccinationUiState())
    val uiState: StateFlow<AddVaccinationUiState> = _uiState.asStateFlow()

    // Snapshot of the persisted vaccination items used by Edit mode.
    // This prevents validation/save from depending solely on transient Compose selection state.
    private var originalVaccinationItems: List<VaccinationItem> = emptyList()

    // The doctorId recorded on the vaccination being edited (if any). Kept separate from
    // selectedDoctor so the doctor list can include this doctor even if they're now inactive.
    private val editingDoctorId = MutableStateFlow<String?>(null)

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
            _uiState.update { it.copy(isVaccinationLoading = true) }
            val vaccination = vaccinationRepository.getVaccinationById(vaccinationId) ?: run {
                _uiState.update { it.copy(isVaccinationLoading = false) }
                return@launch
            }
            android.util.Log.d("EditVaxDebug", "Loaded vaccination id=${vaccination.id} items=${vaccination.items.map { "vaccineId=${it.vaccineId} batchId=${it.batchId} qty=${it.quantity}" }}")
            loadPatient(vaccination.patientId)

            // Wait until the inventory contains the vaccines AND their referenced batches.
            // getInventoryItems() combines vaccine and batch flows, so its first non-empty
            // emission can contain vaccines before the batch query has emitted. Waiting only
            // for inventory.isNotEmpty() caused Edit Vaccination to restore rows with a
            // missing batch and later fail with "Please select vaccine and batch for all rows."
            // Bounded: if a vaccine or batch this vaccination references was later deleted from
            // the catalog, this condition can never become true - an unbounded wait here used to
            // leave originalVaccinationItems at its empty default, and a save (even one that only
            // touched an unrelated field like payment) would then silently persist an empty item
            // list, wiping the vaccine names off this record. Fall through after the timeout so
            // the screen still loads (existing item data is preserved as-is; see the guard in
            // saveVaccination()).
            val waitResult = withTimeoutOrNull(5000) {
                uiState.filter { state ->
                    android.util.Log.d("EditVaxDebug", "inventory check: inventorySize=${state.inventory.size} inventoryIds=${state.inventory.map { it.id }}")
                    vaccination.items.all { item ->
                        val vaccine = state.inventory.firstOrNull { it.id == item.vaccineId }
                        val matched = vaccine != null && vaccine.batches.any { it.batchId == item.batchId }
                        android.util.Log.d("EditVaxDebug", "item vaccineId=${item.vaccineId} batchId=${item.batchId} -> vaccineFound=${vaccine != null} vaccineBrand=${vaccine?.brandName} batchIdsOnVaccine=${vaccine?.batches?.map { it.batchId }} matched=$matched")
                        matched
                    }
                }.first()
            }
            android.util.Log.d("EditVaxDebug", "wait finished, timedOut=${waitResult == null}")

            if (vaccination.doctorId.isNotBlank()) {
                editingDoctorId.value = vaccination.doctorId
                // Wait for the doctor list to include this doctor (covers the inactive-doctor
                // case above). Falls back to "list is non-empty" so we don't hang forever if the
                // doctor record was deleted entirely rather than just deactivated.
                withTimeoutOrNull(5000) {
                    uiState.filter { state ->
                        state.allDoctors.any { it.employeeId == vaccination.doctorId || it.id == vaccination.doctorId }
                    }.first()
                }
            } else if (_uiState.value.allDoctors.isEmpty()) {
                uiState.filter { it.allDoctors.isNotEmpty() }.first()
            }

            val inventory = _uiState.value.inventory
            originalVaccinationItems = vaccination.items

            val items = vaccination.items.map { item ->
                val vaccine = inventory.firstOrNull { it.id == item.vaccineId }
                // Do not depend on the batch being present in the filtered UI list.
                // An old batch can have zero stock and therefore be unavailable in the
                // dropdown, while it is still a valid batch reference for this vaccination.
                val batch = inventory
                    .firstOrNull { it.id == item.vaccineId }
                    ?.batches
                    ?.firstOrNull { it.batchId == item.batchId }
                    ?: inventoryRepository.getBatchById(item.batchId)
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
                it.employeeId == vaccination.doctorId || it.id == vaccination.doctorId
            }

            _uiState.update { it.copy(
                existingVaccinationId = vaccinationId,
                givenDate = vaccination.dateGiven,
                selectedDoctor = existingDoctor ?: it.selectedDoctor,
                vaccinesGiven = if (items.isNotEmpty()) items else listOf(VaccineSelectionState()),
                nextVaccinations = nextStates,
                cashAmount = vaccination.cashAmount.toInt().toString(),
                onlineAmount = vaccination.onlineAmount.toInt().toString(),
                totalAmount = vaccination.totalPaid,
                isVaccinationLoading = false
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
            android.util.Log.d("EditVaxDebug", "fetchInventory emitted: size=${items.size} ids=${items.map { it.id }}")
            val types = items.map { it.type }.filter { it.isNotBlank() }.distinct().sorted()
            _uiState.update { it.copy(
                inventory = items,
                availableDueTypes = if (types.isEmpty()) Constants.DUE_VACCINATION_TYPES else types
            ) }
        }.launchIn(viewModelScope)
    }

    private fun fetchDoctors() {
        // Recomputes whenever profiles change OR the vaccination being edited (and its
        // doctorId) becomes known, so an inactive doctor who performed a past vaccination
        // is still selectable/visible when editing that record.
        combine(profileRepository.allProfiles, editingDoctorId) { profiles, editId -> profiles to editId }
            .onEach { (profiles, editId) ->
                val doctors = profiles.filter {
                    it.role == UserRole.doctor &&
                        (it.isActive || (!editId.isNullOrBlank() && (it.employeeId == editId || it.id == editId)))
                }.sortedBy { it.displayName }

                val currentUserId = auth.currentSessionOrNull()?.user?.id
                val currentUserProfile = profiles.find { it.id == currentUserId }
                val defaultDoctor = if (currentUserProfile?.role == UserRole.doctor) currentUserProfile else null

                _uiState.update { state ->
                    val editDoctor = editId?.let { id -> doctors.firstOrNull { it.employeeId == id || it.id == id } }
                    state.copy(
                        allDoctors = doctors,
                        selectedDoctor = editDoctor ?: if (state.selectedDoctor == null) defaultDoctor else state.selectedDoctor
                    )
                }
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

    fun saveVaccination(
        editVaccineBatch: Boolean = true,
        editQuantity: Boolean = true
    ) {
        val state = _uiState.value
        val patient = state.patient ?: return

        if (state.selectedDoctor == null) {
            _uiState.update { it.copy(doctorError = true, errorMessage = "Please select a doctor.") }
            return
        }

        val isEdit = !state.existingVaccinationId.isNullOrBlank()

        // Defense in depth: if this is an edit that isn't touching Vaccine & Batch, the save
        // path below relies on originalVaccinationItems (the persisted item snapshot from
        // loadVaccination()). If that snapshot never populated - e.g. loadVaccination() was
        // still waiting on inventory data, or timed out because a referenced vaccine/batch was
        // deleted from the catalog - saving here would silently persist an empty item list and
        // wipe the vaccine names off this record. Refuse rather than corrupt existing data.
        if (isEdit && !editVaccineBatch && originalVaccinationItems.isEmpty()) {
            _uiState.update { it.copy(
                errorMessage = "Vaccine details haven't finished loading yet. Please wait a moment and try again, or check \"Vaccine & Batch\" to re-enter them."
            ) }
            return
        }

        if (state.vaccinesGiven.any { it.selectedVaccine == null || it.selectedBatch == null }) {
            if (!isEdit || editVaccineBatch) {
                _uiState.update { it.copy(errorMessage = "Please select vaccine and batch for all rows.") }
                return
            }
        }

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

                val existingVaccination = if (isEdit) {
                    vaccinationRepository.getVaccinationById(vaccinationId)
                } else null

                val items = if (isEdit && !editVaccineBatch) {
                    originalVaccinationItems.mapIndexed { index, original ->
                        val row = state.vaccinesGiven.getOrNull(index)
                        original.copy(
                            id = original.id.ifBlank { UUID.randomUUID().toString() },
                            vaccinationId = vaccinationId,
                            quantity = if (editQuantity) row?.quantity ?: original.quantity else original.quantity
                        )
                    }
                } else {
                    state.vaccinesGiven.map { selection ->
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
                    doctorId = state.selectedDoctor.employeeId ?: state.selectedDoctor.id,
                    performedBy = state.selectedDoctor.displayName,
                    items = items,
                    nextVaccinations = emptyList()
                )

                if (isEdit && existingVaccination != null) {
                    val reminderSpecs = nextRows.map { next ->
                        VaccinationEditEngine.ReminderSpec(
                            type = next.type,
                            vaccineNames = next.nextVaccines.map { it.brandName },
                            vaccineIds = next.nextVaccines.map { it.id },
                            dueDate = next.dueDate,
                            notes = "Scheduled during visit on ${state.givenDate}"
                        )
                    }

                    // All edit side effects are diff-driven. Unchanged inventory, finance,
                    // vaccination-item identity, and reminders produce no transactions.
                    vaccinationEditEngine.execute(
                        original = existingVaccination,
                        updated = vaccination,
                        user = user,
                        reminderSpecs = reminderSpecs
                    )
                } else {
                    // New vaccination keeps the existing creation workflow.
                    clinicalService.recordVaccination(vaccination, user, isNew = true)

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
                }

                _uiState.update { it.copy(isLoading = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }
}
