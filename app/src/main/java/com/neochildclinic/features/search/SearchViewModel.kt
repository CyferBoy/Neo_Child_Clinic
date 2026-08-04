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

    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SearchUiState> = _query
        .debounce(300)
        .flatMapLatest { q ->
            if (q.isBlank()) {
                flowOf(SearchUiState())
            } else {
                searchPatientsUseCase(q)
                    .map { patients ->
                        SearchUiState(
                            query = q,
                            results = patients,
                            isLoading = false
                        )
                    }
                    .onStart { emit(SearchUiState(query = q, isLoading = true)) }
                    .catch { e -> emit(SearchUiState(query = q, error = e.message)) }
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
}
