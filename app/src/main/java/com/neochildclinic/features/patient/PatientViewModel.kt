package com.neochildclinic.features.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.data.local.entity.AuditLogEntity
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.data.local.entity.PatientNotesEntity
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.ConsultationRepository
import com.neochildclinic.domain.repository.DocumentRepository
import io.github.jan.supabase.storage.FileObject
import com.neochildclinic.domain.usecase.patient.DeletePatientUseCase
import com.neochildclinic.domain.usecase.patient.GetPatientByIdUseCase
import com.neochildclinic.domain.usecase.patient.GetPatientsUseCase
import com.neochildclinic.domain.usecase.patient.SavePatientUseCase
import com.neochildclinic.domain.usecase.sync.RefreshDataUseCase
import com.neochildclinic.domain.usecase.vaccination.DeleteVaccinationUseCase
import com.neochildclinic.domain.usecase.vaccination.GetVaccinationsUseCase
import com.neochildclinic.domain.usecase.vaccination.SaveVaccinationUseCase
import com.neochildclinic.core.utils.PatientUtils
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PatientVaccinationCardData(
    val vaccination: Vaccination,
    val reminders: List<ReminderEntity>
)

@HiltViewModel
class PatientViewModel @Inject constructor(
    private val getPatientsUseCase: GetPatientsUseCase,
    private val getPatientByIdUseCase: GetPatientByIdUseCase,
    private val getVaccinationsUseCase: GetVaccinationsUseCase,
    private val savePatientUseCase: SavePatientUseCase,
    private val deletePatientUseCase: DeletePatientUseCase,
    private val saveVaccinationUseCase: SaveVaccinationUseCase,
    private val deleteVaccinationUseCase: DeleteVaccinationUseCase,
    private val refreshDataUseCase: RefreshDataUseCase,
    private val patientRepository: PatientRepository,
    private val reminderRepository: ReminderRepository,
    private val consultationRepository: ConsultationRepository,
    private val profileRepository: com.neochildclinic.domain.repository.ProfileRepository,
    private val inventoryRepository: com.neochildclinic.domain.repository.InventoryRepository,
    private val documentRepository: DocumentRepository,
    private val database: AppDatabase,
    private val auth: Auth,
    private val postgrest: Postgrest
) : ViewModel() {

    private val _documents = MutableStateFlow<List<FileObject>>(emptyList())
    val documents: StateFlow<List<FileObject>> = _documents.asStateFlow()

    fun loadDocuments(patientId: String) {
        viewModelScope.launch {
            try {
                _documents.value = documentRepository.listDocuments(patientId)
            } catch (_: Exception) {}
        }
    }

    fun uploadDocument(patientId: String, fileName: String, bytes: ByteArray) {
        viewModelScope.launch {
            try {
                documentRepository.uploadDocument(patientId, fileName, bytes)
                loadDocuments(patientId)
            } catch (_: Exception) {}
        }
    }

    suspend fun getDocumentUrl(path: String): String {
        return documentRepository.getDownloadUrl(path)
    }

    fun deleteDocument(path: String, patientId: String) {
        viewModelScope.launch {
            try {
                documentRepository.deleteDocument(path)
                loadDocuments(patientId)
            } catch (_: Exception) {}
        }
    }
    
    val allPatients: StateFlow<List<Patient>>
    val allVaccinations: StateFlow<List<Vaccination>>
    val patientsWithMissingPrice: StateFlow<Set<String>>
    val doctorMap: StateFlow<Map<String, String>>
    val vaccineMap: StateFlow<Map<String, String>>
    
    private val _profile = MutableStateFlow<Profile?>(null)
    val currentProfile: StateFlow<Profile?> = _profile.asStateFlow()

    init {
        fetchProfile()
        // State Streams
        allPatients = getPatientsUseCase().stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

        allVaccinations = getVaccinationsUseCase().stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

        patientsWithMissingPrice = allVaccinations.map { vaccinations ->
            vaccinations.filter { it.totalPaid <= 0.0 }.map { it.patientId }.toSet()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

        doctorMap = profileRepository.allProfiles
            .map { profiles -> profiles.associate { it.employeeId.orEmpty() to it.displayName } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyMap()
            )

        vaccineMap = inventoryRepository.getInventoryItems()
            .map { items -> items.associate { it.id to it.brandName } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyMap()
            )

    }

    private fun fetchProfile() {
        val currentUser = auth.currentSessionOrNull()?.user ?: return
        
        viewModelScope.launch {
            try {
                val profile = postgrest.from("profiles").select {
                    filter { eq("id", currentUser.id) }
                }.decodeSingleOrNull<Profile>()

                val authLastLogin = currentUser.lastSignInAt?.toString()

                if (profile != null) {
                    if (profile.lastLogin != authLastLogin) {
                        val updatedProfile = profile.copy(lastLogin = authLastLogin)
                        postgrest.from("profiles").update(updatedProfile) {
                            filter { eq("id", profile.id) }
                        }
                        _profile.value = updatedProfile
                    } else {
                        _profile.value = profile
                    }
                } else {
                    val email = currentUser.email
                    if (email != null) {
                        var profileByEmail = postgrest.from("profiles").select {
                            filter { eq("email", email) }
                        }.decodeSingleOrNull<Profile>()
                        
                        if (profileByEmail != null) {
                            if (profileByEmail.lastLogin != authLastLogin) {
                                profileByEmail = profileByEmail.copy(lastLogin = authLastLogin)
                                postgrest.from("profiles").update(profileByEmail) {
                                    filter { eq("id", profileByEmail.id) }
                                }
                            }
                            _profile.value = profileByEmail
                            return@launch
                        }
                    }

                    _profile.value = Profile(
                        id = currentUser.id,
                        email = currentUser.email ?: "",
                        displayName = currentUser.userMetadata?.get("name")?.toString() ?: currentUser.email?.substringBefore("@") ?: "User",
                        phoneNumber = currentUser.userMetadata?.get("phone_number")?.toString() ?: "",
                        employeeId = currentUser.userMetadata?.get("employee_id")?.toString(),
                        role = UserRole.nurse,
                        lastLogin = authLastLogin
                    )
                }
            } catch (_: Exception) { }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshDataUseCase()
        }
    }

    fun deletePatient(id: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                deletePatientUseCase(id)
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    suspend fun getPatientById(id: String): Patient? {
        return getPatientByIdUseCase(id)
    }

    fun savePatient(patient: Patient, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                savePatientUseCase(patient)
                onComplete()
            } catch (e: Exception) {
                // Handle validation or save error
            }
        }
    }

    fun deleteVaccination(id: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                deleteVaccinationUseCase(id)
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun deleteConsultation(id: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                consultationRepository.deleteConsultation(id)
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun saveVaccination(vaccination: Vaccination, onComplete: () -> Unit) {
        viewModelScope.launch {
            saveVaccinationUseCase(vaccination)
            onComplete()
        }
    }

    fun getPatientHistory(patientId: String): Flow<List<Vaccination>> {
        return getVaccinationsUseCase.forPatient(patientId).map { vaccinations ->
            vaccinations.sortedByDescending { PatientUtils.parseDate(it.dateGiven)?.time ?: 0L }
        }
    }

    fun getPatientConsultations(patientId: String): Flow<List<Consultation>> {
        return consultationRepository.getConsultationsForPatient(patientId)
    }

    fun getAuditLogs(patientId: String): Flow<List<AuditLogEntity>> {
        return patientRepository.getPatientTimeline(patientId)
    }

    fun getPatientReminders(patientId: String): Flow<List<ReminderEntity>> {
        return reminderRepository.getPatientReminders(patientId)
    }

    /**
     * Emits a complete vaccination-card data set from the same local snapshot.
     * The UI no longer renders vaccination history first and attaches reminders
     * in a later, independent emission.
     */
    fun getPatientVaccinationCards(patientId: String): Flow<List<PatientVaccinationCardData>> =
        combine(
            getPatientHistory(patientId),
            reminderRepository.getPatientReminders(patientId)
        ) { vaccinations, reminders ->
            val remindersByVisit = reminders.groupBy { it.originalVisitId }
            vaccinations.map { vaccination ->
                PatientVaccinationCardData(
                    vaccination = vaccination,
                    reminders = remindersByVisit[vaccination.id].orEmpty()
                )
            }
        }

    fun getPatientNotes(patientId: String): Flow<List<PatientNotesEntity>> {
        return patientRepository.getNotes(patientId)
    }

    suspend fun getInventoryDeductions(vaccinationId: String): List<com.neochildclinic.data.local.entity.InventoryDeductionEntity> {
        return database.inventoryDeductionDao().getForVaccination(vaccinationId)
    }
}
