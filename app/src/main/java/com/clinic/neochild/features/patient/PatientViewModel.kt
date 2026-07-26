package com.clinic.neochild.features.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.data.local.entity.AuditLogEntity
import com.clinic.neochild.data.local.database.AppDatabase
import com.clinic.neochild.data.local.entity.ReminderEntity
import com.clinic.neochild.data.local.entity.PatientNotesEntity
import com.clinic.neochild.data.remote.mapper.FirestoreMappers
import com.clinic.neochild.domain.model.Staff
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.repository.PatientRepository
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.domain.usecase.patient.DeletePatientUseCase
import com.clinic.neochild.domain.usecase.patient.GetPatientByIdUseCase
import com.clinic.neochild.domain.usecase.patient.GetPatientsUseCase
import com.clinic.neochild.domain.usecase.patient.SavePatientUseCase
import com.clinic.neochild.domain.usecase.sync.RefreshDataUseCase
import com.clinic.neochild.domain.usecase.vaccination.DeleteVaccinationUseCase
import com.clinic.neochild.domain.usecase.vaccination.GetVaccinationsUseCase
import com.clinic.neochild.domain.usecase.vaccination.SaveVaccinationUseCase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.clinic.neochild.core.utils.PatientUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

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
    private val database: AppDatabase,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : ViewModel() {
    
    val allPatients: StateFlow<List<Patient>>
    val allVaccinations: StateFlow<List<Vaccination>>
    val patientsWithMissingPrice: StateFlow<Set<String>>
    
    private val _staff = MutableStateFlow<Staff?>(null)
    val currentStaff: StateFlow<Staff?> = _staff.asStateFlow()

    init {
        fetchStaffProfile()
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
            vaccinations.filter { it.cost <= 0.0 }.map { it.patientId }.toSet()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

        refresh()
    }

    private fun fetchStaffProfile() {
        val currentUser = auth.currentUser ?: return
        
        viewModelScope.launch {
            try {
                val doc = db.collection("staff").document(currentUser.uid).get().await()
                if (doc.exists()) {
                    _staff.value = FirestoreMappers.toStaff(doc)
                } else {
                    val email = currentUser.email
                    if (email != null) {
                        val query = db.collection("staff")
                            .whereEqualTo("email", email)
                            .get().await()
                        
                        if (query.documents.isNotEmpty()) {
                            val staffDoc = query.documents.first()
                            _staff.value = FirestoreMappers.toStaff(staffDoc)
                            return@launch
                        }
                    }

                    _staff.value = Staff(
                        id = currentUser.uid,
                        email = currentUser.email ?: "",
                        name = currentUser.displayName ?: currentUser.email?.substringBefore("@") ?: "User",
                        role = "User",
                        createdAt = currentUser.metadata?.creationTimestamp ?: 0L
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

    fun deletePatient(id: String) {
        viewModelScope.launch {
            deletePatientUseCase(id)
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

    fun getAuditLogs(patientId: String): Flow<List<AuditLogEntity>> {
        return patientRepository.getPatientTimeline(patientId)
    }

    fun getPatientFollowUps(patientId: String): Flow<List<ReminderEntity>> {
        return reminderRepository.getPatientFollowUps(patientId)
    }

    fun getPatientNotes(patientId: String): Flow<List<PatientNotesEntity>> {
        return patientRepository.getNotes(patientId)
    }

    suspend fun getInventoryDeductions(vaccinationId: String): List<com.clinic.neochild.data.local.entity.InventoryDeductionEntity> {
        return database.inventoryDeductionDao().getForVaccination(vaccinationId)
    }
}
