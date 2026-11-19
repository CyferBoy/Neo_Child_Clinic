package com.neochildclinic.feature.patient.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.feature.patient.domain.MergePatientsUseCase
import com.neochildclinic.core.sync.RefreshDataUseCase
import com.neochildclinic.core.sync.RefreshTables
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.core.security.CurrentUserProvider
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.core.sync.RealtimeChangeSubscriptions
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PatientSortOption {
    NAME_AZ,
    NEWEST
}

/** State emitted when the upstream patient flow fails - keeps the screen out of a permanent spinner. */
internal fun patientLoadErrorState(error: Throwable): PatientListUiState =
    PatientListUiState(isLoading = false, error = error.message ?: "Could not load patients")

data class PatientListUiState(
    val patients: List<Patient> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val sortOption: PatientSortOption = PatientSortOption.NAME_AZ,
    val isMergeSelectionMode: Boolean = false,
    val selectedPatients: Set<String> = emptySet(),
    val isMerging: Boolean = false,
    val patientsWithMissingPrice: Set<String> = emptySet(),
    val totalCount: Int = 0,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class PatientListViewModel @Inject constructor(
    private val mergePatientsUseCase: MergePatientsUseCase,
    private val refreshDataUseCase: RefreshDataUseCase,
    private val patientRepository: PatientRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val realtimeChangeSubscriptions: RealtimeChangeSubscriptions
) : ViewModel() {

    /** Read once per screen-open; gates are UX-only, RLS enforces independently. */
    val currentUserRole: UserRole? = currentUserProvider.getCurrentUserRole()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(PatientSortOption.NAME_AZ)
    private val _isMergeSelectionMode = MutableStateFlow(false)
    private val _selectedPatients = MutableStateFlow<Set<String>>(emptySet())
    private val _isMerging = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _isRefreshing = MutableStateFlow(false)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    private val _debouncedSearchQuery = _searchQuery.debounce(300).distinctUntilChanged()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val uiState: StateFlow<PatientListUiState> = combine(
        _debouncedSearchQuery.flatMapLatest { query ->
            if (query.isBlank()) patientRepository.allPatients
            else patientRepository.searchPatients("%$query%")
        },
        _sortOption,
        patientRepository.patientIdsWithMissingPrice,
        combine(_isMergeSelectionMode, _selectedPatients, _isMerging, _error, _isRefreshing) { mode, selected, merging, err, refreshing ->
            RefreshState(mode, selected, merging, err, refreshing)
        },
        patientRepository.getPatientCount()
    ) { patients, sort, missingPrice, internalState, total ->

        val sorted = when (sort) {
            PatientSortOption.NAME_AZ -> patients.sortedBy { it.name.lowercase() }
            PatientSortOption.NEWEST -> patients.sortedByDescending { it.registrationDate }
        }

        PatientListUiState(
            patients = sorted,
            isLoading = false,
            searchQuery = _searchQuery.value,
            sortOption = sort,
            isMergeSelectionMode = internalState.mode,
            selectedPatients = internalState.selected,
            isMerging = internalState.merging,
            error = internalState.error,
            patientsWithMissingPrice = missingPrice,
            totalCount = total,
            isRefreshing = internalState.refreshing
        )
    }.flowOn(Dispatchers.Default).catch { e ->
        if (e is CancellationException) throw e
        emit(patientLoadErrorState(e))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PatientListUiState(isLoading = true))

    init {
        // Entry and realtime events pull only the three tables this screen shows (plus
        // their FK dependencies) - not the 13-step whole-database pipeline. Manual
        // pull-to-refresh keeps the full pipeline (refresh() with no scope).
        refreshPatientsAndVisits()
        observeRealtimeChanges()
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeRealtimeChanges() {
        viewModelScope.launch {
            // "patient_visits" is the real table name; there is no "vaccinations" table, so
            // subscribing to it silently never fired. Patient visits are what change the
            // missing-price badge, so they must be in the list.
            // debounce(500) coalesces a burst of postgres_changes (a single save typically
            // produces several: patient_visits + reminders + finance_transactions +
            // vaccination_items) into one refresh instead of one 15-table full sync each.
            realtimeChangeSubscriptions.tableChanges(
                "patients-db-changes", "patients", "patient_visits", "consultations"
            ).debounce(500).onEach {
                refreshPatientsAndVisits()
            }.catch { e ->
                Log.e("Realtime", "Error in patient realtime changes", e)
            }.collect()
        }
    }

    // Scope covering patients, patient_visits and consultations (the tables the screen
    // subscribes to). Kept next to refresh() so the full/scoped split stays obvious.
    private fun refreshPatientsAndVisits() =
        refresh(RefreshTables.PATIENTS, RefreshTables.VISITS, RefreshTables.CONSULTATIONS)

    fun refresh(vararg tables: RefreshTables) {
        // Guard: without this, N realtime events become N concurrent full syncs.
        // A refresh already in flight will pick up everything the burst contains anyway.
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.update { true }
            try {
                refreshDataUseCase(*tables)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.update { "Refresh failed: ${e.message}" }
            } finally {
                _isRefreshing.update { false }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.update { query }
    }

    fun updateSortOption(option: PatientSortOption) {
        _sortOption.update { option }
    }

    fun toggleSelection(patient: Patient) {
        var isNowMergeMode = false
        _selectedPatients.update { currentSelected ->
            val newSelected = if (patient.id in currentSelected) {
                currentSelected - patient.id
            } else {
                currentSelected + patient.id
            }
            isNowMergeMode = newSelected.isNotEmpty()
            newSelected
        }
        _isMergeSelectionMode.update { isNowMergeMode }
    }

    fun clearSelection() {
        _selectedPatients.update { emptySet() }
        _isMergeSelectionMode.update { false }
    }

    fun enterMergeMode(initialPatient: Patient) {
        _selectedPatients.update { setOf(initialPatient.id) }
        _isMergeSelectionMode.update { true }
    }

    fun deletePatient(id: String) {
        viewModelScope.launch {
            try {
                patientRepository.deletePatient(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PatientListVM", "Delete patient failed", e)
                _error.update { "Delete failed: ${e.message}" }
            }
        }
    }

    fun mergeSelectedPatients(master: Patient) {
        // Selection holds ids so a pull emitting fresh Patient objects cannot drop it.
        val secondaryId = _selectedPatients.value.firstOrNull { it != master.id }
        if (secondaryId != null) {
            viewModelScope.launch {
                _isMerging.update { true }
                try {
                    mergePatientsUseCase(master.id, listOf(secondaryId))
                    clearSelection()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _error.update { e.message }
                } finally {
                    _isMerging.update { false }
                }
            }
        }
    }

    fun clearError() {
        _error.update { null }
    }
}

private data class RefreshState<A, B, C, D, E>(val mode: A, val selected: B, val merging: C, val error: D, val refreshing: E)
