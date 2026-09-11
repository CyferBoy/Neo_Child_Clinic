package com.neochildclinic.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.repository.DashboardRepository
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.domain.repository.PatientTodoRepository
import com.neochildclinic.domain.repository.ProfileRepository
import com.neochildclinic.domain.usecase.doctor.GetAvailableSlotsUseCase
import com.neochildclinic.core.ui.SlotsUiState
import com.neochildclinic.core.ui.loadUiState
import io.github.jan.supabase.auth.Auth
import com.neochildclinic.data.local.entity.ConsultationTodoEntity
import com.neochildclinic.data.local.entity.VaccinationTodoEntity
import com.neochildclinic.domain.model.Patient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.repository.SyncState
import com.neochildclinic.core.network.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
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
    val isOnline: Boolean = false,
    val pendingSyncCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val todayConsultations: List<ConsultationTodoEntity> = emptyList(),
    val todayVaccinations: List<VaccinationTodoEntity> = emptyList(),
    val visitedConsultations: List<ConsultationTodoEntity> = emptyList(),
    val visitedVaccinations: List<VaccinationTodoEntity> = emptyList(),
    val datesWithData: Set<String> = emptySet(),
    val patients: List<Patient> = emptyList(),
    // Today's Patient doctor+slot picker (req. 15/16) - doctor assignment is optional at
    // this quick-add stage (unlike Add Consultation/Add Vaccination, where it's
    // mandatory), so a receptionist who doesn't yet know the assigned doctor can still
    // add the patient to today's list; the notification simply broadcasts to all doctors
    // in that case, same as before this feature existed.
    val allDoctors: List<Profile> = emptyList(),
    val todoSlotsState: SlotsUiState = SlotsUiState.Idle
)

/**
 * Orchestrates Dashboard data using unified data streams.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val syncRepository: SyncRepository,
    private val networkMonitor: NetworkMonitor,
    private val patientRepository: PatientRepository,
    private val patientTodoRepository: PatientTodoRepository,
    private val realtime: Realtime,
    private val profileRepository: ProfileRepository,
    private val getAvailableSlotsUseCase: GetAvailableSlotsUseCase,
    private val auth: Auth,
) : ViewModel() {

    private val _allDoctors = MutableStateFlow<List<Profile>>(emptyList())
    private val _todoSlotsState = MutableStateFlow<SlotsUiState>(SlotsUiState.Idle)

    private var todoSlotLoadToken = 0

    private val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
    private val _selectedDate = MutableStateFlow(todayStr)
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    init {
        viewModelScope.launch { runCatching { patientTodoRepository.refresh() } }
        observeTodayPatientRealtimeChanges()
        observeDoctors()
    }

    private fun observeDoctors() {
        profileRepository.allProfiles
            .map { profiles -> profiles.filter { it.role == UserRole.doctor && it.isActive }.sortedBy { it.displayName } }
            .onEach { doctors -> _allDoctors.value = doctors }
            .launchIn(viewModelScope)
    }

    /** Loads available slots for the Today's Patient quick-add dialog (req. 15/16/22). */
    fun loadTodoSlots(doctorId: String?, date: String) {
        if (doctorId.isNullOrBlank() || date.isBlank()) {
            _todoSlotsState.value = SlotsUiState.Idle
            return
        }
        val token = ++todoSlotLoadToken
        _todoSlotsState.value = SlotsUiState.Loading
        viewModelScope.launch {
            val result = getAvailableSlotsUseCase.loadUiState(doctorId, date)
            if (token != todoSlotLoadToken) return@launch
            _todoSlotsState.value = result
        }
    }

    fun clearTodoSlots() {
        _todoSlotsState.value = SlotsUiState.Idle
    }

    // Mirrors PatientListViewModel.observeRealtimeChanges(): Realtime here is only ever a
    // trigger to re-run the existing pull-side refresh(), never a second write path into
    // Room. This is what makes "doctor app open -> new patient appears without a manual
    // refresh" (req. 3/4) work without a second Today's Patient sync framework - the existing
    // sync_queue/SyncWorker path remains the only thing that ever pushes local -> cloud.
    private fun observeTodayPatientRealtimeChanges() {
        viewModelScope.launch {
            try {
                realtime.subscriptions["realtime:todays-patients-db-changes"]?.let {
                    realtime.removeChannel(it)
                }

                val channel = realtime.channel("todays-patients-db-changes")

                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "consultation_todos"
                }.onEach {
                    runCatching { patientTodoRepository.refresh() }
                }.catch { e ->
                    android.util.Log.e("Realtime", "Error in consultation_todos change flow", e)
                }.launchIn(this)

                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "vaccination_todos"
                }.onEach {
                    runCatching { patientTodoRepository.refresh() }
                }.catch { e ->
                    android.util.Log.e("Realtime", "Error in vaccination_todos change flow", e)
                }.launchIn(this)

                channel.subscribe()
            } catch (e: Exception) {
                android.util.Log.e("Realtime", "Error setting up Today's Patient realtime changes", e)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCleared() {
        super.onCleared()
        val channelId = "realtime:todays-patients-db-changes"
        val channel = realtime.subscriptions[channelId]
        if (channel != null) {
            GlobalScope.launch {
                runCatching { realtime.removeChannel(channel) }
            }
        }
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
        combine(
            syncRepository.syncState,
            syncRepository.getPendingCount(),
            networkMonitor.isOnline
        ) { syncState, pendingCount, isOnline ->
            Triple(syncState, pendingCount, isOnline)
        },
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
        combine(
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
            patientRepository.allPatients,
            _allDoctors,
            _todoSlotsState
        ) { dates, patients, doctors, slots ->
            listOf(dates, patients, doctors, slots)
        }
    ) { stats, sync, todos, extra ->
        DashboardUiState(
            patientCount = stats[0] as Int,
            lowStockCount = stats[1] as Int,
            borrowedCount = stats[2] as Int,
            dueTodayCount = stats[3] as Int,
            wasteCount = stats[4] as Int,
            syncState = sync.first,
            isOnline = sync.third,
            pendingSyncCount = sync.second,
            todayConsultations = todos[0] as List<ConsultationTodoEntity>,
            todayVaccinations = todos[1] as List<VaccinationTodoEntity>,
            visitedConsultations = todos[2] as List<ConsultationTodoEntity>,
            visitedVaccinations = todos[3] as List<VaccinationTodoEntity>,
            datesWithData = extra[0] as Set<String>,
            patients = extra[1] as List<Patient>,
            allDoctors = extra[2] as List<Profile>,
            todoSlotsState = extra[3] as SlotsUiState
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
        address: String,
        doctorId: String? = null,
        doctorName: String? = null,
        availabilitySlotId: String? = null
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
                    doctorId = doctorId,
                    doctorName = doctorName,
                    availabilitySlotId = availabilitySlotId,
                    updatedAt = now
                )
            } else {
                ConsultationTodoEntity(
                    patientId = patientId,
                    name = name,
                    mobile = mobile,
                    address = address,
                    todoDate = _selectedDate.value,
                    doctorId = doctorId,
                    doctorName = doctorName,
                    availabilitySlotId = availabilitySlotId,
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
        vaccineNames: String,
        doctorId: String? = null,
        doctorName: String? = null,
        availabilitySlotId: String? = null
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
                    doctorId = doctorId,
                    doctorName = doctorName,
                    availabilitySlotId = availabilitySlotId,
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
                    doctorId = doctorId,
                    doctorName = doctorName,
                    availabilitySlotId = availabilitySlotId,
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
