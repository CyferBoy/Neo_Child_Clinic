package com.neochildclinic.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.usecase.patient.SearchPatientsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<Patient> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchPatientsUseCase: SearchPatientsUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0)

    // Kept so pull-to-refresh can re-run the current query without blanking the list
    // (which would flash the skeleton); only a genuinely new query starts from empty.
    private var lastResults: List<Patient> = emptyList()

    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SearchUiState> = combine(_query, _refreshTrigger) { q, _ -> q }
        .debounce(300)
        .flatMapLatest { q ->
            if (q.isBlank()) {
                flowOf(SearchUiState())
            } else {
                searchPatientsUseCase(q)
                    .map { patients ->
                        lastResults = patients
                        SearchUiState(
                            query = q,
                            results = patients,
                            isLoading = false
                        )
                    }
                    .onStart { emit(SearchUiState(query = q, results = lastResults, isLoading = true)) }
                    .catch { e -> emit(SearchUiState(query = q, results = lastResults, error = e.message)) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SearchUiState()
        )

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun refresh() {
        if (_query.value.isNotBlank()) _refreshTrigger.value++
    }
}
