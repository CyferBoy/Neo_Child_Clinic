package com.neochildclinic.feature.patient.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.InventoryDeduction
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.PatientNote
import com.neochildclinic.domain.model.Reminder
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.domain.model.PatientDocument
import com.neochildclinic.feature.patient.domain.repository.PatientRepository
import com.neochildclinic.feature.vaccination.domain.repository.VaccinationRepository
import com.neochildclinic.feature.consultation.domain.repository.ConsultationRepository
import com.neochildclinic.feature.patient.domain.repository.DocumentRepository
import com.neochildclinic.feature.audit.domain.repository.AuditLogRepository
import com.neochildclinic.feature.sync.domain.RefreshDataUseCase
import com.neochildclinic.core.common.PatientUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PatientVaccinationCardData(
    val vaccination: Vaccination,
    val reminders: List<Reminder>
)

@HiltViewModel
class PatientViewModel @Inject constructor(
    private val vaccinationRepository: VaccinationRepository,
    private val refreshDataUseCase: RefreshDataUseCase,
    private val patientRepository: PatientRepository,
    private val consultationRepository: ConsultationRepository,
    private val profileRepository: com.neochildclinic.feature.profile.domain.repository.ProfileRepository,
    private val inventoryRepository: com.neochildclinic.feature.inventory.domain.repository.InventoryRepository,
    private val documentRepository: DocumentRepository,
    private val auditLogRepository: AuditLogRepository
) : ViewModel() {

    private val _documents = MutableStateFlow<List<PatientDocument>>(emptyList())
    val documents: StateFlow<List<PatientDocument>> = _documents.asStateFlow()

    private val _documentError = MutableStateFlow<String?>(null)
    val documentError: StateFlow<String?> = _documentError.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Vaccination ids with a delete transaction currently running. Guards against a rapid
    // double-tap on Delete (or any other double-invocation) firing the deletion transaction
    // twice concurrently, and lets the confirmation dialog show a busy state instead of
    // dismissing instantly - the local transaction is atomic either way (see
    // VaccinationRepository.deleteVaccination), but skipping the redundant second call
    // here avoids doing the same work twice and any flicker that would cause in the UI.
    private val _deletingVaccinationIds = MutableStateFlow<Set<String>>(emptySet())
    val deletingVaccinationIds: StateFlow<Set<String>> = _deletingVaccinationIds.asStateFlow()

    fun loadDocuments(patientId: String) {
        viewModelScope.launch {
            try {
                _documents.value = documentRepository.listDocuments(patientId)
                _documentError.value = null
            } catch (e: Exception) {
                _documentError.value = e.message ?: "Failed to load documents"
            }
        }
    }

    fun uploadDocument(patientId: String, fileName: String, bytes: ByteArray) {
        viewModelScope.launch {
            try {
                documentRepository.uploadDocument(patientId, fileName, bytes)
                loadDocuments(patientId)
            } catch (e: Exception) {
                _documentError.value = e.message ?: "Upload failed"
            }
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
            } catch (e: Exception) {
                _documentError.value = e.message ?: "Delete failed"
            }
        }
    }

    fun clearDocumentError() {
        _documentError.value = null
    }
    
    val allPatients: StateFlow<List<Patient>>
    val doctorMap: StateFlow<Map<String, String>>
    val vaccineMap: StateFlow<Map<String, String>>

    init {
        // State Streams
        allPatients = patientRepository.allPatients.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
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

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                refreshDataUseCase()
            } catch (_: Exception) {}
            _isRefreshing.value = false
        }
    }

    fun deletePatient(id: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                patientRepository.deletePatient(id)
                onResult(true)
            } catch (e: Exception) {
                android.util.Log.e("PatientVM", "Delete patient failed", e)
                onResult(false)
            }
        }
    }

    suspend fun getPatientById(id: String): Patient? {
        return patientRepository.getPatientById(id)
    }

    fun savePatient(patient: Patient, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                if (patient.name.isBlank() || patient.dob.isBlank()) {
                    throw IllegalArgumentException("Patient name and Date of Birth are required.")
                }
                patientRepository.addPatient(patient)
                onComplete()
            } catch (e: Exception) {
                // Handle validation or save error
            }
        }
    }

    fun deleteVaccination(id: String, onResult: (Boolean) -> Unit = {}) {
        if (id in _deletingVaccinationIds.value) return // already in flight; ignore the repeat tap
        _deletingVaccinationIds.value += id
        viewModelScope.launch {
            try {
                vaccinationRepository.deleteVaccination(id)
                onResult(true)
            } catch (e: Exception) {
                android.util.Log.e("PatientVM", "Delete vaccination failed", e)
                onResult(false)
            } finally {
                _deletingVaccinationIds.value -= id
            }
        }
    }

    fun deleteConsultation(id: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                consultationRepository.deleteConsultation(id)
                onResult(true)
            } catch (e: Exception) {
                android.util.Log.e("PatientVM", "Delete consultation failed", e)
                onResult(false)
            }
        }
    }

    fun getPatientConsultations(patientId: String): Flow<List<Consultation>> {
        return consultationRepository.getConsultationsForPatient(patientId)
            .map { consultations ->
                consultations.sortedByDescending { PatientUtils.parseDate(it.date)?.time ?: 0L }
            }
    }

    /**
     * Patient audit history is online-only (see PatientAuditLogPager) - the dialog drives this
     * directly via load()/loadMore()/clear() rather than through a Flow.
     */
    val auditLogPager = com.neochildclinic.feature.audit.presentation.PatientAuditLogPager(auditLogRepository, viewModelScope)

    /**
     * Emits each patient's vaccination history as one Room transaction snapshot.
     * The visit, vaccination items, and reminders are loaded together so the
     * card is not rendered first and then completed by a later reminder flow.
     */
    fun getPatientVaccinationCards(patientId: String): Flow<List<PatientVaccinationCardData>> =
        vaccinationRepository.getVaccinationCardsForPatient(patientId)
            .map { snapshots ->
                snapshots
                    .filter { it.vaccination.visitType == "VACCINATION" }
                    .sortedByDescending { PatientUtils.parseDate(it.vaccination.dateGiven)?.time ?: 0L }
                    .map { snapshot ->
                        PatientVaccinationCardData(
                            vaccination = snapshot.vaccination,
                            reminders = snapshot.reminders
                        )
                    }
            }
            .flowOn(Dispatchers.Default)

    fun getPatientNotes(patientId: String): Flow<List<PatientNote>> {
        return patientRepository.getNotes(patientId)
    }

    suspend fun getInventoryDeductions(vaccinationId: String): List<InventoryDeduction> {
        return inventoryRepository.getInventoryDeductionsForVaccination(vaccinationId)
    }
}
