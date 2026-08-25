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

    private val today = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())

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
        patientTodoRepository.getTodayConsultations(today),
        patientTodoRepository.getTodayVaccinations(today),
        patientRepository.allPatients
    ) { stats, sync, consultations, vaccinations, patients ->
        DashboardUiState(
            patientCount = stats[0] as Int,
            lowStockCount = stats[1] as Int,
            borrowedCount = stats[2] as Int,
            dueTodayCount = stats[3] as Int,
            wasteCount = stats[4] as Int,
            syncState = sync,
            todayConsultations = consultations,
            todayVaccinations = vaccinations,
            patients = patients
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState(isLoading = true))

    fun addConsultation(patient: Patient) {
        viewModelScope.launch {
            val now = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
            patientTodoRepository.addConsultation(
                ConsultationTodoEntity(
                    patientId = patient.id, name = patient.name, mobile = patient.phone,
                    address = patient.address.orEmpty(), todoDate = today,
                    createdAt = now, updatedAt = now
                )
            )
        }
    }

    fun addVaccination(patient: Patient, vaccineNames: String) {
        viewModelScope.launch {
            val now = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
            patientTodoRepository.addVaccination(
                VaccinationTodoEntity(
                    patientId = patient.id, name = patient.name, mobile = patient.phone,
                    vaccineNames = vaccineNames, address = patient.address.orEmpty(), todoDate = today,
                    createdAt = now, updatedAt = now
                )
            )
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
