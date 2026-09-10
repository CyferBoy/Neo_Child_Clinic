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

data class NextVaccinationItem(
    val id: String = UUID.randomUUID().toString(),
    val reminderId: String? = null,
    val type: String = "",
    val vaccine: InventoryItem? = null,
    val typeError: Boolean = false
)

data class NextVaccinationGroup(
    val id: String = UUID.randomUUID().toString(),
    val dueDate: String = "",
    val items: List<NextVaccinationItem> = listOf(NextVaccinationItem())
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
    val nextVaccinationGroups: List<NextVaccinationGroup> = emptyList(),
    val cashAmount: String = "0",
    val onlineAmount: String = "0",
    val totalAmount: Double = 0.0,
    val withFees: Boolean = false,
    val doctorsAcc: Boolean = false,
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
    private val cancelledNextReminderIds = mutableSetOf<String>()

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
        cancelledNextReminderIds.clear()
        viewModelScope.launch {
            _uiState.update { it.copy(isVaccinationLoading = true) }
            val vaccination = vaccinationRepository.getVaccinationById(vaccinationId) ?: run {
                _uiState.update { it.copy(isVaccinationLoading = false) }
                return@launch
            }
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
            withTimeoutOrNull(5000) {
                uiState.filter { state ->
                    vaccination.items.all { item ->
                        val vaccine = state.inventory.firstOrNull { it.id == item.vaccineId }
                        vaccine != null && vaccine.batches.any { it.batchId == item.batchId }
                    }
                }.first()
            }

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
                    ?: InventoryItem(
                        id = item.vaccineId,
                        brandName = item.vaccineName.ifBlank { "Saved vaccine" },
                        stock = 0,
                        type = "",
                        company = ""
                    )
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
            val groups = reminders.filter { it.status == "ACTIVE" && it.reminderEnabled }
                .groupBy { it.dueDate }
                .map { (dueDate, groupReminders) ->
                    NextVaccinationGroup(
                        dueDate = dueDate,
                        items = groupReminders.flatMap { reminder ->
                            val nextVaccineIds = reminder.nxtVaccineId ?: emptyList()
                            if (nextVaccineIds.isEmpty()) {
                                listOf(NextVaccinationItem(reminderId = reminder.id, type = reminder.type, vaccine = null))
                            } else {
                                nextVaccineIds.map { id ->
                                    NextVaccinationItem(
                                        reminderId = reminder.id,
                                        type = reminder.type,
                                        vaccine = inventory.find { it.id == id }
                                    )
                                }
                            }
                        }
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
                nextVaccinationGroups = groups,
                cashAmount = vaccination.cashAmount.toInt().toString(),
                onlineAmount = vaccination.onlineAmount.toInt().toString(),
                totalAmount = vaccination.totalPaid,
                withFees = vaccination.withFees,
                doctorsAcc = vaccination.doctorsAcc,
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
        _uiState.update { state ->
            // Re-validate every row's current batch selection against the new given date -
            // a batch that's still in stock but expires before the new date is no longer a
            // valid selection and must be swapped for the next valid batch (or cleared).
            val revalidated = state.vaccinesGiven.map { row ->
                val batch = row.selectedBatch
                if (batch == null || !InventoryUtils.isExpiredAsOf(batch.expiryDate, date)) {
                    row
                } else {
                    val replacement = row.selectedVaccine?.batches
                        ?.filter { it.remainingQuantity > 0 && !InventoryUtils.isExpiredAsOf(it.expiryDate, date) }
                        ?.minByOrNull { PatientUtils.parseDate(it.expiryDate) ?: Date(Long.MAX_VALUE) }
                    row.copy(selectedBatch = replacement)
                }
            }
            state.copy(givenDate = date, vaccinesGiven = revalidated)
        }
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
        val givenDate = _uiState.value.givenDate
        val bestBatch = vaccine.batches
            .filter { it.remainingQuantity > 0 && !InventoryUtils.isExpiredAsOf(it.expiryDate, givenDate) }
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

    fun updateWithFees(enabled: Boolean) {
        _uiState.update { it.copy(withFees = enabled) }
    }

    fun updateDoctorsAccount(enabled: Boolean) {
        _uiState.update { it.copy(doctorsAcc = enabled) }
    }

    fun addNextVaccinationGroup() {
        _uiState.update { it.copy(nextVaccinationGroups = it.nextVaccinationGroups + NextVaccinationGroup()) }
    }

    fun removeNextVaccinationGroup(groupId: String) {
        _uiState.update { state ->
            state.copy(nextVaccinationGroups = state.nextVaccinationGroups.filter { it.id != groupId })
        }
    }

    fun updateNextVaccinationGroupDate(groupId: String, dueDate: String) {
        _uiState.update { state ->
            state.copy(nextVaccinationGroups = state.nextVaccinationGroups.map { group ->
                if (group.id == groupId) group.copy(dueDate = dueDate) else group
            })
        }
    }

    fun addNextVaccinationItem(groupId: String) {
        _uiState.update { state ->
            state.copy(nextVaccinationGroups = state.nextVaccinationGroups.map { group ->
                if (group.id == groupId) group.copy(items = group.items + NextVaccinationItem()) else group
            })
        }
    }

    fun removeNextVaccinationItem(groupId: String, itemId: String) {
        _uiState.update { state ->
            state.copy(nextVaccinationGroups = state.nextVaccinationGroups.map { group ->
                if (group.id == groupId) {
                    val updatedItems = group.items.filter { it.id != itemId }
                    group.copy(items = updatedItems.ifEmpty { listOf(NextVaccinationItem()) })
                } else group
            })
        }
    }

    fun updateNextVaccinationItem(groupId: String, itemId: String, type: String? = null, vaccine: InventoryItem? = null) {
        _uiState.update { state ->
            state.copy(nextVaccinationGroups = state.nextVaccinationGroups.map { group ->
                if (group.id == groupId) {
                    group.copy(items = group.items.map { item ->
                        if (item.id == itemId) {
                            item.copy(
                                type = type ?: item.type,
                                vaccine = if (type != null) null else (vaccine ?: item.vaccine),
                                typeError = if (type != null) false else item.typeError
                            )
                        } else item
                    })
                } else group
            })
        }
    }

    fun cancelNextVaccinationGroup(groupId: String) {
        val group = _uiState.value.nextVaccinationGroups.find { it.id == groupId } ?: return
        viewModelScope.launch {
            try {
                val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"
                group.items.forEach { item ->
                    item.reminderId?.let { rId ->
                        val reminder = reminderRepository.getReminderById(rId) ?: return@let
                        reminderRepository.dismissReminder(reminder, "Cancelled from Next Vaccination Group", user)
                        cancelledNextReminderIds += rId
                    }
                }
                _uiState.update { state ->
                    state.copy(nextVaccinationGroups = state.nextVaccinationGroups.filter { it.id != groupId })
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Unable to cancel group") }
            }
        }
    }

    fun cancelNextVaccinationItem(groupId: String, itemId: String) {
        val group = _uiState.value.nextVaccinationGroups.find { it.id == groupId } ?: return
        val item = group.items.find { it.id == itemId } ?: return
        val reminderId = item.reminderId

        if (reminderId.isNullOrBlank()) {
            removeNextVaccinationItem(groupId, itemId)
            return
        }

        viewModelScope.launch {
            try {
                val reminder = reminderRepository.getReminderById(reminderId) ?: return@launch
                val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"

                if (reminder.nxtVaccineId != null && reminder.nxtVaccineId.size > 1 && item.vaccine != null) {
                    // It's a multi-vaccine reminder, but our UI redesign treats them as separate items.
                    // The underlying repository logic for cancelNextVaccinationVaccine expects the ID.
                    reminderRepository.cancelNextVaccinationVaccine(
                        reminder = reminder,
                        vaccineId = item.vaccine.id,
                        reason = "Cancelled from Next Vaccination",
                        performedBy = user
                    )
                } else {
                    // Single vaccine or type-only reminder
                    reminderRepository.dismissReminder(reminder, "Cancelled from Next Vaccination", user)
                    cancelledNextReminderIds += reminderId
                }

                _uiState.update { state ->
                    state.copy(nextVaccinationGroups = state.nextVaccinationGroups.map { g ->
                        if (g.id == groupId) {
                            val updated = g.items.filter { it.id != itemId }
                            g.copy(items = updated.ifEmpty { listOf(NextVaccinationItem()) })
                        } else g
                    })
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Unable to cancel item") }
            }
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

        val nextGroups = state.nextVaccinationGroups
        var firstInvalidGroup: String? = null
        var firstInvalidItem: String? = null

        nextGroups.forEach { group ->
            if (group.dueDate.isBlank()) {
                if (firstInvalidGroup == null) firstInvalidGroup = group.id
            }
            group.items.forEach { item ->
                if (item.type.isBlank()) {
                    if (firstInvalidGroup == null) firstInvalidGroup = group.id
                    if (firstInvalidItem == null) firstInvalidItem = item.id
                }
            }
        }

        if (firstInvalidGroup != null) {
            _uiState.update { s ->
                s.copy(
                    errorMessage = "Each Next Vaccination entry requires a Type and Due Date.",
                    nextVaccinationGroups = s.nextVaccinationGroups.map { g ->
                        g.copy(items = g.items.map { it.copy(typeError = it.type.isBlank()) })
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
                    withFees = state.withFees,
                    doctorsAcc = state.doctorsAcc,
                    doctorId = state.selectedDoctor.employeeId ?: state.selectedDoctor.id,
                    performedBy = state.selectedDoctor.displayName,
                    items = items,
                    nextVaccinations = emptyList(),
                    status = com.neochildclinic.domain.model.ReminderStatus.COMPLETED
                )

                // Transform grouped UI state back to flat ReminderSpec list
                val reminderSpecs = nextGroups.flatMap { group ->
                    // Group items by Type under the same Date to keep compatibility with existing ReminderRepository logic
                    // which expects a list of vaccines for a single type.
                    // Actually, the requirements say "Each Type + Vaccine combination creates a separate reminder item".
                    // But the existing repository saveNextVaccination takes List<String> vaccineNames.
                    // If we want "separate reminder item" for each combo, we should send them one by one.
                    // HOWEVER, if they have the same Type and same Date, they are usually grouped in this app.
                    // Let's stick to the "Each combo is a row" in UI, but keep the "Type-based grouping" for saving
                    // if they share the same Type and Date, OR just send them as individual specs.
                    // The requirement says: 15 Oct 2026 Booster -> DPT, Primary -> MMR, Optional -> NULL
                    // creates 3 separate reminders.
                    group.items.map { item ->
                        VaccinationEditEngine.ReminderSpec(
                            type = item.type,
                            vaccineNames = listOfNotNull(item.vaccine?.brandName),
                            vaccineIds = listOfNotNull(item.vaccine?.id),
                            dueDate = group.dueDate,
                            notes = "Scheduled during visit on ${state.givenDate}"
                        )
                    }
                }

                if (isEdit && existingVaccination != null) {
                    // All edit side effects are diff-driven. Unchanged inventory, finance,
                    // vaccination-item identity, and reminders produce no transactions.
                    vaccinationEditEngine.execute(
                        original = existingVaccination,
                        updated = vaccination,
                        user = user,
                        reminderSpecs = reminderSpecs,
                        excludedReminderIds = cancelledNextReminderIds
                    )
                } else {
                    // New vaccination keeps the existing creation workflow.
                    clinicalService.recordVaccination(vaccination, user, isNew = true)

                    reminderSpecs.forEach { spec ->
                        reminderRepository.saveNextVaccination(
                            patientId = patient.id,
                            originalVisitId = vaccinationId,
                            type = spec.type,
                            vaccineNames = spec.vaccineNames,
                            nxtVaccineId = spec.vaccineIds,
                            dueDate = spec.dueDate,
                            notes = spec.notes,
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
