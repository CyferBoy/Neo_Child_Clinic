package com.neochildclinic.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.repository.BorrowRepository
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.domain.repository.PatientTodoRepository
import com.neochildclinic.domain.repository.ProfileRepository
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.WasteRepository
import com.neochildclinic.domain.repository.DoctorAvailabilityRepository
import com.neochildclinic.domain.usecase.doctor.GetAvailableSlotsUseCase
import com.neochildclinic.domain.model.DoctorAvailabilityResult
import com.neochildclinic.domain.model.TimeRange
import com.neochildclinic.core.ui.SlotsUiState
import com.neochildclinic.core.ui.loadUiState
import com.neochildclinic.core.utils.DateClassifier
import com.neochildclinic.core.utils.DateCategory
import com.neochildclinic.data.manager.RealtimeChangeSubscriptions
import com.neochildclinic.data.local.entity.ConsultationTodoEntity
import com.neochildclinic.data.local.entity.VaccinationTodoEntity
import com.neochildclinic.domain.model.Patient
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.neochildclinic.data.repository.SyncRepositoryImpl
import com.neochildclinic.data.repository.SyncState
import com.neochildclinic.core.network.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val patientCount: Int = 0,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0,
    val borrowedCount: Int = 0,
    val dueTodayCount: Int = 0,
    val wasteCount: Int = 0,
    val syncState: SyncState = SyncState.IDLE,
    val isOnline: Boolean = false,
    val pendingSyncCount: Int = 0,
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
    val todoSlotsState: SlotsUiState = SlotsUiState.Idle,
    // Dynamic slot filter segments for the selected date (derived from doctor
    // availability + the day's bookings - never hard-coded). The control is shown
    // only when there are 2+ segments; selectedSlotKey is null when filtering is off.
    val slotSegments: List<SlotSegment> = emptyList(),
    val selectedSlotKey: String? = null
)

/** Orchestrates Dashboard data using unified data streams. */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val syncRepository: SyncRepositoryImpl,
    private val networkMonitor: NetworkMonitor,
    private val patientRepository: PatientRepository,
    private val patientTodoRepository: PatientTodoRepository,
    private val inventoryRepository: InventoryRepository,
    private val reminderRepository: ReminderRepository,
    private val wasteRepository: WasteRepository,
    private val borrowRepository: BorrowRepository,
    private val realtimeChangeSubscriptions: RealtimeChangeSubscriptions,
    private val profileRepository: ProfileRepository,
    private val getAvailableSlotsUseCase: GetAvailableSlotsUseCase,
    private val doctorAvailabilityRepository: DoctorAvailabilityRepository
) : ViewModel() {

    private val _allDoctors = MutableStateFlow<List<Profile>>(emptyList())
    private val _todoSlotsState = MutableStateFlow<SlotsUiState>(SlotsUiState.Idle)
    private val _selectedSlotKey = MutableStateFlow<String?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private data class SlotFilterState(
        val segments: List<SlotSegment> = emptyList(),
        val ranges: Map<String, TimeRange> = emptyMap(),
        val effectiveKey: String? = null
    )

    private data class TodoBundle(val todos: List<Any>, val slots: SlotFilterState)

    private var todoSlotLoadToken = 0

    private val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH))
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

    fun setSelectedSlot(key: String) {
        _selectedSlotKey.value = key
    }

    /**
     * Resolves the dynamic slot filter inputs for one date: segments come from every
     * active doctor's availability (GetAvailableSlotsUseCase - the same source the
     * add/edit dialogs use) plus any range already booked that day (so a patient whose
     * slot is exception-blocked still has a reachable segment), and ranges resolve each
     * of the day's availabilitySlotIds to its time range for list filtering.
     */
    private suspend fun computeSlotFilter(
        todos: List<Any>,
        doctors: List<Profile>,
        selectedKey: String?,
        date: String
    ): SlotFilterState = try {
        val availability = doctors.flatMap { doctor ->
            (getAvailableSlotsUseCase(doctor.id, date) as? DoctorAvailabilityResult.Available)
                ?.slots.orEmpty()
        }.map { TimeRange(it.startMinute, it.endMinute) }

        val slotIds = buildSet {
            (todos[0] as List<ConsultationTodoEntity>).forEach { add(it.availabilitySlotId) }
            (todos[1] as List<VaccinationTodoEntity>).forEach { add(it.availabilitySlotId) }
            (todos[2] as List<ConsultationTodoEntity>).forEach { add(it.availabilitySlotId) }
            (todos[3] as List<VaccinationTodoEntity>).forEach { add(it.availabilitySlotId) }
        }.filterNotNull().filter { it.isNotBlank() }

        val ranges = slotIds.mapNotNull { id ->
            doctorAvailabilityRepository.getWeeklySlotById(id)?.timeRange?.let { id to it }
        }.toMap()

        val segments = TodaySlotFilter.segments(availability, ranges.values.toList())
        SlotFilterState(segments, ranges, TodaySlotFilter.effectiveKey(segments, selectedKey))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        // A slot-lookup failure must never take the patient list down with it.
        SlotFilterState()
    }

    // Mirrors PatientListViewModel.observeRealtimeChanges(): Realtime here is only ever a
    // trigger to re-run the existing pull-side refresh(), never a second write path into
    // Room. This is what makes "doctor app open -> new patient appears without a manual
    // refresh" (req. 3/4) work without a second Today's Patient sync framework - the existing
    // sync_queue/SyncWorker path remains the only thing that ever pushes local -> cloud.
    // Channel teardown is handled by RealtimeChangeSubscriptions when viewModelScope cancels.
    private fun observeTodayPatientRealtimeChanges() {
        viewModelScope.launch {
            realtimeChangeSubscriptions.tableChanges(
                "todays-patients-db-changes", "consultation_todos", "vaccination_todos"
            ).onEach {
                runCatching { patientTodoRepository.refresh() }
            }.collect()
        }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(
            patientCount(),
            inventoryStockCounts(),
            borrowedCount(),
            dueCount(),
            wasteCount()
        ) { values -> values.toList() },
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
            }.flatMapLatest { todos ->
                combine(_allDoctors, _selectedSlotKey) { doctors, key -> doctors to key }
                    .flatMapLatest { (doctors, selectedKey) ->
                        flow {
                            emit(TodoBundle(todos, computeSlotFilter(todos, doctors, selectedKey, date)))
                        }
                    }
            }
        },
        combine(
            _selectedDate.flatMapLatest { date ->
                val selected = try {
                    java.time.LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH))
                } catch (_: java.time.format.DateTimeParseException) {
                    java.time.LocalDate.now()
                }
                val start = selected.withDayOfMonth(1)
                val end = start.plusMonths(1).minusDays(1)
                val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
                patientTodoRepository.getDatesWithData(start.format(fmt), end.format(fmt)).map { it.toSet() }
            },
            patientRepository.allPatients,
            _allDoctors,
            _todoSlotsState
        ) { dates, patients, doctors, slots ->
            listOf(dates, patients, doctors, slots)
        }
    ) { stats, sync, bundle, extra ->
        val slots = bundle.slots
        val todos = bundle.todos
        fun pass(slotId: String?): Boolean =
            TodaySlotFilter.passes(slots.effectiveKey, slots.ranges, slotId)
        DashboardUiState(
            patientCount = stats[0] as Int,
            lowStockCount = (stats[1] as Pair<Int, Int>).first,
            borrowedCount = stats[2] as Int,
            dueTodayCount = stats[3] as Int,
            wasteCount = stats[4] as Int,
            outOfStockCount = (stats[1] as Pair<Int, Int>).second,
            syncState = sync.first,
            isOnline = sync.third,
            pendingSyncCount = sync.second,
            todayConsultations = (todos[0] as List<ConsultationTodoEntity>).filter { pass(it.availabilitySlotId) },
            todayVaccinations = (todos[1] as List<VaccinationTodoEntity>).filter { pass(it.availabilitySlotId) },
            visitedConsultations = (todos[2] as List<ConsultationTodoEntity>).filter { pass(it.availabilitySlotId) },
            visitedVaccinations = (todos[3] as List<VaccinationTodoEntity>).filter { pass(it.availabilitySlotId) },
            datesWithData = extra[0] as Set<String>,
            patients = extra[1] as List<Patient>,
            allDoctors = extra[2] as List<Profile>,
            todoSlotsState = extra[3] as SlotsUiState,
            slotSegments = slots.segments,
            selectedSlotKey = slots.effectiveKey
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

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

    fun deleteConsultation(id: String) {
        viewModelScope.launch {
            try {
                patientTodoRepository.deleteConsultation(id)
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "Delete consultation failed", e)
            }
        }
    }
    fun deleteVaccination(id: String) {
        viewModelScope.launch {
            try {
                patientTodoRepository.deleteVaccination(id)
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "Delete vaccination failed", e)
            }
        }
    }

    private fun patientCount(): Flow<Int> = patientRepository.getPatientCount()

    private fun inventoryStockCounts(): Flow<Pair<Int, Int>> = inventoryRepository.getInventoryItems().map { items ->
        items.count { it.isLowStock && !it.hasOutofStock } to items.count { it.hasOutofStock }
    }

    private fun borrowedCount(): Flow<Int> = borrowRepository.getActiveBorrowedRecords().map { it.size }

    private fun dueCount(): Flow<Int> = reminderRepository.getDueList().map { list ->
        val todayCal = DateClassifier.getTodayStart()
        list.count {
            val cat = DateClassifier.classify(it.nextDueDate, todayCal)
            cat is DateCategory.Today
        }
    }

    private fun wasteCount(): Flow<Int> = wasteRepository.getWasteCount()

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                syncRepository.processNextItems()
            } catch (e: Exception) {
                // Handle error
            }
            _isRefreshing.value = false
        }
    }
}
