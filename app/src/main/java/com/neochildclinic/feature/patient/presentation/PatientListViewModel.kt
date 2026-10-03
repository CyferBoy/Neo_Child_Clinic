package com.neochildclinic.feature.patient.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.feature.patient.domain.MergePatientsUseCase
import com.neochildclinic.feature.patient.domain.SearchPatientsUseCase
import com.neochildclinic.feature.sync.domain.RefreshDataUseCase
import com.neochildclinic.feature.patient.domain.repository.PatientRepository
import com.neochildclinic.core.security.CurrentUserProvider
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.core.sync.RealtimeChangeSubscriptions
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PatientSortOption {
    NAME_AZ,
    NEWEST
}

data class PatientListUiState(
    val patients: List<Patient> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val sortOption: PatientSortOption = PatientSortOption.NAME_AZ,
    val isMergeSelectionMode: Boolean = false,
    val selectedPatients: Set<Patient> = emptySet(),
    val isMerging: Boolean = false,
    val patientsWithMissingPrice: Set<String> = emptySet(),
    val totalCount: Int = 0,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class PatientListViewModel @Inject constructor(
    private val mergePatientsUseCase: MergePatientsUseCase,
    private val searchPatientsUseCase: SearchPatientsUseCase,
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
    private val _selectedPatients = MutableStateFlow<Set<Patient>>(emptySet())
    private val _isMerging = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _isRefreshing = MutableStateFlow(false)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    private val _debouncedSearchQuery = _searchQuery.debounce(300).distinctUntilChanged()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val uiState: StateFlow<PatientListUiState> = combine(
        _debouncedSearchQuery.flatMapLatest { searchPatientsUseCase(it) },
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
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PatientListUiState(isLoading = true))

    init {
        refresh()
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
                refresh()
            }.catch { e ->
                Log.e("Realtime", "Error in patient realtime changes", e)
            }.collect()
        }
    }

    fun refresh() {
        // Guard: without this, N realtime events become N concurrent 15-table full syncs.
        // A refresh already in flight will pick up everything the burst contains anyway.
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                refreshDataUseCase()
            } catch (e: Exception) {
                _error.value = "Refresh failed: ${e.message}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSortOption(option: PatientSortOption) {
        _sortOption.value = option
    }

    fun toggleSelection(patient: Patient) {
        val currentSelected = _selectedPatients.value
        val newSelected = if (currentSelected.contains(patient)) {
            currentSelected - patient
        } else {
            currentSelected + patient
        }
        _selectedPatients.value = newSelected
        _isMergeSelectionMode.value = newSelected.isNotEmpty()
    }

    fun clearSelection() {
        _selectedPatients.value = emptySet()
        _isMergeSelectionMode.value = false
    }

    fun enterMergeMode(initialPatient: Patient) {
        _selectedPatients.value = setOf(initialPatient)
        _isMergeSelectionMode.value = true
    }

    fun deletePatient(id: String) {
        viewModelScope.launch {
            try {
                patientRepository.deletePatient(id)
            } catch (e: Exception) {
                Log.e("PatientListVM", "Delete patient failed", e)
                _error.value = "Delete failed: ${e.message}"
            }
        }
    }

    fun mergeSelectedPatients(master: Patient) {
        val selected = _selectedPatients.value
        val secondary = selected.find { it != master }
        if (secondary != null) {
            viewModelScope.launch {
                _isMerging.value = true
                try {
                    mergePatientsUseCase(master.id, listOf(secondary.id))
                    clearSelection()
                } catch (e: Exception) {
                    _error.value = e.message
                } finally {
                    _isMerging.value = false
                }
            }
        }
    }

    fun autoMergeDuplicates() {
        viewModelScope.launch {
            _isMerging.value = true
            try {
                val patientsList = uiState.value.patients
                val groups = patientsList.groupBy { 
                    it.name.trim().lowercase() + "|" + it.phone.trim()
                }.filter { it.value.size > 1 }

                for (group in groups.values) {
                    val master = group[0]
                    val duplicates = group.drop(1).map { it.id }
                    mergePatientsUseCase(master.id, duplicates)
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isMerging.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

private data class RefreshState<A, B, C, D, E>(val mode: A, val selected: B, val merging: C, val error: D, val refreshing: E)
