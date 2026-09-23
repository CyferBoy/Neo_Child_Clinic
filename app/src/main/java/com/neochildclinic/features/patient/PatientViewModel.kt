package com.neochildclinic.features.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.data.local.entity.PatientNotesEntity
import com.neochildclinic.data.local.entity.toVaccination
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.data.repository.PatientRepositoryImpl
import com.neochildclinic.data.repository.VaccinationRepositoryImpl
import com.neochildclinic.data.repository.ConsultationRepositoryImpl
import com.neochildclinic.data.repository.DocumentRepositoryImpl
import io.github.jan.supabase.storage.FileObject
import com.neochildclinic.domain.usecase.sync.RefreshDataUseCase
import com.neochildclinic.core.utils.PatientUtils
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
    private val vaccinationRepository: VaccinationRepositoryImpl,
    private val refreshDataUseCase: RefreshDataUseCase,
    private val patientRepository: PatientRepositoryImpl,
    private val consultationRepository: ConsultationRepositoryImpl,
    private val profileRepository: com.neochildclinic.data.repository.ProfileRepositoryImpl,
    private val inventoryRepository: com.neochildclinic.data.repository.InventoryRepositoryImpl,
    private val documentRepository: DocumentRepositoryImpl,
    private val database: AppDatabase,
    private val postgrest: Postgrest
) : ViewModel() {

    private val _documents = MutableStateFlow<List<FileObject>>(emptyList())
    val documents: StateFlow<List<FileObject>> = _documents.asStateFlow()

    private val _documentError = MutableStateFlow<String?>(null)
    val documentError: StateFlow<String?> = _documentError.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

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
        viewModelScope.launch {
            try {
                vaccinationRepository.deleteVaccination(id)
                onResult(true)
            } catch (e: Exception) {
                android.util.Log.e("PatientVM", "Delete vaccination failed", e)
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
    val auditLogPager = com.neochildclinic.features.audit.PatientAuditLogPager(postgrest, viewModelScope)

    /**
     * Emits each patient's vaccination history as one Room transaction snapshot.
     * The visit, vaccination items, and reminders are loaded together so the
     * card is not rendered first and then completed by a later reminder flow.
     */
    fun getPatientVaccinationCards(patientId: String): Flow<List<PatientVaccinationCardData>> =
        vaccinationRepository.getVaccinationCardsForPatient(patientId)
            .map { snapshots ->
                snapshots
                    .filter { it.visit.visitType == "VACCINATION" }
                    .sortedByDescending { PatientUtils.parseDate(it.visit.dateGiven)?.time ?: 0L }
                    .map { snapshot ->
                        PatientVaccinationCardData(
                            vaccination = snapshot.visit.toVaccination().copy(
                                items = snapshot.items
                            ),
                            reminders = snapshot.reminders
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
