package com.neochildclinic.features.personalreminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.constants.Constants
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.PersonalReminderEntity
import com.neochildclinic.data.local.entity.VaccineEntity
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.domain.repository.PersonalReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// Sentinel vaccine-id used in the UI when the requirement isn't for a specific
// catalog vaccine. Never persisted as vaccine_id - see AddEditPersonalReminderViewModel.save().
const val OTHER_VACCINE_SENTINEL = "__OTHER__"

data class AddEditPersonalReminderUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,

    val patientQuery: String = "",
    val patientResults: List<Patient> = emptyList(),
    val selectedPatient: Patient? = null,

    val vaccines: List<VaccineEntity> = emptyList(),
    val selectedVaccineId: String? = null, // null = nothing chosen yet; OTHER_VACCINE_SENTINEL = "Other"

    val note: String = "",

    val advanceReceived: Boolean = false,
    val advanceAmount: String = "",
    val advanceDate: String = "",

    val reminderDate: String = "",

    val patientError: Boolean = false,
    val vaccineError: Boolean = false,
    val reminderDateError: Boolean = false,
    val advanceAmountError: Boolean = false,
    val advanceDateError: Boolean = false,

    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddEditPersonalReminderViewModel @Inject constructor(
    private val repository: PersonalReminderRepository,
    private val patientRepository: PatientRepository,
    database: AppDatabase
) : ViewModel() {

    private val vaccineDao = database.vaccineDao()

    private val _uiState = MutableStateFlow(AddEditPersonalReminderUiState())
    val uiState: StateFlow<AddEditPersonalReminderUiState> = _uiState.asStateFlow()

    private val _patientQuery = MutableStateFlow("")
    private var editingReminderId: String? = null

    init {
        viewModelScope.launch {
            vaccineDao.getAllVaccines().collect { vaccines ->
                _uiState.update { it.copy(vaccines = vaccines.sortedBy { v -> v.brandName }) }
            }
        }

        // Live patient search-as-you-type, mirroring SearchViewModel's debounce pattern.
        viewModelScope.launch {
            searchPatientResults().collect { results ->
                _uiState.update { it.copy(patientResults = results) }
            }
        }
    }

    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun searchPatientResults(): Flow<List<Patient>> =
        _patientQuery
            .debounce(250)
            .flatMapLatest { q ->
                if (q.isBlank()) flowOf(emptyList()) else patientRepository.searchPatients(q)
            }

    fun loadForEdit(reminderId: String) {
        editingReminderId = reminderId
        _uiState.update { it.copy(isEditing = true, isLoading = true) }
        viewModelScope.launch {
            val reminder = repository.getById(reminderId)
            if (reminder == null) {
                _uiState.update { it.copy(isLoading = false, error = "Reminder not found.") }
                return@launch
            }
            val patient = patientRepository.getPatientById(reminder.patientId)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    selectedPatient = patient,
                    selectedVaccineId = reminder.vaccineId ?: if (reminder.vaccineLabel != null) OTHER_VACCINE_SENTINEL else null,
                    note = reminder.note ?: "",
                    advanceReceived = reminder.advanceReceived,
                    advanceAmount = reminder.advanceAmount?.let { amt -> if (amt == amt.toLong().toDouble()) amt.toLong().toString() else amt.toString() } ?: "",
                    advanceDate = reminder.advanceDate ?: "",
                    reminderDate = reminder.reminderDate
                )
            }
        }
    }

    // Optional convenience entry point: pre-select a patient when the screen is
    // opened from that patient's own record (does not lock the field - the user
    // can still change it).
    fun preselectPatient(patientId: String) {
        viewModelScope.launch {
            val patient = patientRepository.getPatientById(patientId) ?: return@launch
            _uiState.update { it.copy(selectedPatient = patient, patientQuery = "") }
        }
    }

    fun onPatientQueryChange(query: String) {
        _uiState.update { it.copy(patientQuery = query, patientError = false) }
        _patientQuery.value = query
    }

    fun selectPatient(patient: Patient) {
        _uiState.update {
            it.copy(selectedPatient = patient, patientQuery = "", patientResults = emptyList(), patientError = false)
        }
    }

    fun clearSelectedPatient() {
        _uiState.update { it.copy(selectedPatient = null) }
    }

    fun selectVaccine(vaccineId: String) {
        _uiState.update { it.copy(selectedVaccineId = vaccineId, vaccineError = false) }
    }

    fun onNoteChange(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun onAdvanceReceivedChange(received: Boolean) {
        _uiState.update {
            it.copy(
                advanceReceived = received,
                advanceAmountError = false,
                advanceDateError = false
            )
        }
    }

    fun onAdvanceAmountChange(amount: String) {
        // Digits and at most one decimal point only - blocks negative amounts at input time.
        if (amount.isEmpty() || amount.matches(Regex("^\\d*\\.?\\d*$"))) {
            _uiState.update { it.copy(advanceAmount = amount, advanceAmountError = false) }
        }
    }

    fun onAdvanceDateChange(date: String) {
        _uiState.update { it.copy(advanceDate = date, advanceDateError = false) }
    }

    fun onReminderDateChange(date: String) {
        _uiState.update { it.copy(reminderDate = date, reminderDateError = false) }
    }

    fun save() {
        val state = _uiState.value

        val patient = state.selectedPatient
        val amount = state.advanceAmount.toDoubleOrNull()

        val patientError = patient == null
        val vaccineError = state.selectedVaccineId == null
        val reminderDateError = state.reminderDate.isBlank()
        val advanceAmountError = state.advanceReceived && (amount == null || amount < 0.0)
        val advanceDateError = state.advanceReceived && state.advanceDate.isBlank()

        if (patientError || vaccineError || reminderDateError || advanceAmountError || advanceDateError) {
            _uiState.update {
                it.copy(
                    patientError = patientError,
                    vaccineError = vaccineError,
                    reminderDateError = reminderDateError,
                    advanceAmountError = advanceAmountError,
                    advanceDateError = advanceDateError
                )
            }
            return
        }

        val selectedVaccine = state.vaccines.firstOrNull { it.id == state.selectedVaccineId }
        val vaccineId = if (state.selectedVaccineId == OTHER_VACCINE_SENTINEL) null else state.selectedVaccineId
        val vaccineLabel = if (state.selectedVaccineId == OTHER_VACCINE_SENTINEL) "Other" else selectedVaccine?.brandName

        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                val existing = editingReminderId?.let { repository.getById(it) }
                val entity = PersonalReminderEntity(
                    id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                    patientId = patient!!.id,
                    vaccineId = vaccineId,
                    vaccineLabel = vaccineLabel,
                    note = state.note.trim().ifBlank { null },
                    advanceReceived = state.advanceReceived,
                    advanceAmount = if (state.advanceReceived) amount else null,
                    advanceDate = if (state.advanceReceived) state.advanceDate else null,
                    reminderDate = state.reminderDate,
                    status = existing?.status ?: "PENDING",
                    createdAt = existing?.createdAt ?: "",
                    updatedAt = existing?.updatedAt ?: "",
                    completedAt = existing?.completedAt,
                    cancelledAt = existing?.cancelledAt,
                    createdBy = existing?.createdBy,
                    updatedBy = existing?.updatedBy
                )

                if (existing != null) {
                    repository.updateReminder(entity)
                } else {
                    repository.createReminder(entity)
                }

                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "Failed to save reminder.") }
            }
        }
    }

    companion object {
        /** Today's date pre-formatted for the reminder-date field default. */
        fun todayFormatted(): String = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date())
    }
}
