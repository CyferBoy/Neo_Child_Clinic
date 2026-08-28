package com.neochildclinic.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.domain.repository.DashboardRepository
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.domain.repository.PatientTodoRepository
import com.neochildclinic.data.local.entity.ConsultationTodoEntity
import com.neochildclinic.data.local.entity.VaccinationTodoEntity
import com.neochildclinic.domain.model.Patient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.repository.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val patientCount: Int = 0,
    val lowStockCount: Int = 0,
    val borrowedCount: Int = 0,
    val dueTodayCount: Int = 0,
    val wasteCount: Int = 0,
    val syncState: SyncState = SyncState.IDLE,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val todayConsultations: List<ConsultationTodoEntity> = emptyList(),
    val todayVaccinations: List<VaccinationTodoEntity> = emptyList(),
    val visitedConsultations: List<ConsultationTodoEntity> = emptyList(),
    val visitedVaccinations: List<VaccinationTodoEntity> = emptyList(),
    val datesWithData: Set<String> = emptySet(),
    val patients: List<Patient> = emptyList()
)

/**
 * Orchestrates Dashboard data using unified data streams.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val syncRepository: SyncRepository,
    private val patientRepository: PatientRepository,
    private val patientTodoRepository: PatientTodoRepository,
) : ViewModel() {

    private val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
    private val _selectedDate = MutableStateFlow(todayStr)
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    init {
        viewModelScope.launch { runCatching { patientTodoRepository.refresh() } }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(
            dashboardRepository.getPatientCount(),
            dashboardRepository.getLowStockCount(),
            dashboardRepository.getBorrowedCount(),
            dashboardRepository.getDueCount(),
            dashboardRepository.getWasteCount()
        ) { p, low, borrowed, due, waste ->
            listOf(p, low, borrowed, due, waste)
        },
        syncRepository.syncState,
        _selectedDate.flatMapLatest { date ->
            combine(
                patientTodoRepository.getConsultationsByDateAndStatus(date, "PENDING"),
                patientTodoRepository.getVaccinationsByDateAndStatus(date, "PENDING"),
                patientTodoRepository.getConsultationsByDateAndStatus(date, "COMPLETED"),
                patientTodoRepository.getVaccinationsByDateAndStatus(date, "COMPLETED")
            ) { pCons, pVacc, cCons, cVacc ->
                listOf(pCons, pVacc, cCons, cVacc)
            }
        },
        _selectedDate.flatMapLatest { date ->
            val calendar = java.util.Calendar.getInstance()
            calendar.time = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(date) ?: Date()
            calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
            val start = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(calendar.time)
            calendar.add(java.util.Calendar.MONTH, 1)
            calendar.add(java.util.Calendar.DAY_OF_MONTH, -1)
            val end = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(calendar.time)
            patientTodoRepository.getDatesWithData(start, end).map { it.toSet() }
        },
        patientRepository.allPatients
    ) { stats, sync, todos, dates, patients ->
        DashboardUiState(
            patientCount = stats[0] as Int,
            lowStockCount = stats[1] as Int,
            borrowedCount = stats[2] as Int,
            dueTodayCount = stats[3] as Int,
            wasteCount = stats[4] as Int,
            syncState = sync,
            todayConsultations = todos[0] as List<ConsultationTodoEntity>,
            todayVaccinations = todos[1] as List<VaccinationTodoEntity>,
            visitedConsultations = todos[2] as List<ConsultationTodoEntity>,
            visitedVaccinations = todos[3] as List<VaccinationTodoEntity>,
            datesWithData = dates,
            patients = patients
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState(isLoading = true))

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    fun toggleTodoStatus(item: Any) {
        viewModelScope.launch {
            when (item) {
                is ConsultationTodoEntity -> {
                    val newStatus = if (item.status == "PENDING") "COMPLETED" else "PENDING"
                    patientTodoRepository.updateStatus("CONSULTATION_TODO", item.id, newStatus)
                }
                is VaccinationTodoEntity -> {
                    val newStatus = if (item.status == "PENDING") "COMPLETED" else "PENDING"
                    patientTodoRepository.updateStatus("VACCINATION_TODO", item.id, newStatus)
                }
            }
        }
    }

    fun addConsultation(patient: Patient) {
        addConsultationDirect(
            patientId = patient.id,
            name = patient.name,
            mobile = patient.phone,
            address = patient.address.orEmpty()
        )
    }

    fun addConsultationDirect(
        id: String? = null,
        patientId: String? = null,
        name: String,
        mobile: String,
        address: String
    ) {
        viewModelScope.launch {
            val now = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
            val todo = if (id != null) {
                ConsultationTodoEntity(
                    id = id,
                    patientId = patientId,
                    name = name,
                    mobile = mobile,
                    address = address,
                    todoDate = _selectedDate.value,
                    updatedAt = now
                )
            } else {
                ConsultationTodoEntity(
                    patientId = patientId,
                    name = name,
                    mobile = mobile,
                    address = address,
                    todoDate = _selectedDate.value,
                    createdAt = now,
                    updatedAt = now
                )
            }
            patientTodoRepository.addConsultation(todo)
        }
    }

    fun addVaccination(patient: Patient, vaccineNames: String) {
        addVaccinationDirect(
            patientId = patient.id,
            name = patient.name,
            mobile = patient.phone,
            address = patient.address.orEmpty(),
            vaccineNames = vaccineNames
        )
    }

    fun addVaccinationDirect(
        id: String? = null,
        patientId: String? = null,
        name: String,
        mobile: String,
        address: String,
        vaccineNames: String
    ) {
        viewModelScope.launch {
            val now = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
            val todo = if (id != null) {
                VaccinationTodoEntity(
                    id = id,
                    patientId = patientId,
                    name = name,
                    mobile = mobile,
                    vaccineNames = vaccineNames,
                    address = address,
                    todoDate = _selectedDate.value,
                    updatedAt = now
                )
            } else {
                VaccinationTodoEntity(
                    patientId = patientId,
                    name = name,
                    mobile = mobile,
                    vaccineNames = vaccineNames,
                    address = address,
                    todoDate = _selectedDate.value,
                    createdAt = now,
                    updatedAt = now
                )
            }
            patientTodoRepository.addVaccination(todo)
        }
    }

    fun deleteConsultation(id: String) { viewModelScope.launch { patientTodoRepository.deleteConsultation(id) } }
    fun deleteVaccination(id: String) { viewModelScope.launch { patientTodoRepository.deleteVaccination(id) } }

    fun refresh() {
        viewModelScope.launch {
            try {
                dashboardRepository.refreshDashboardData()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
