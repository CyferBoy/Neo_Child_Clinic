package com.neochildclinic.feature.patient.data

import com.neochildclinic.data.local.entity.PatientEntity
import com.neochildclinic.data.local.entity.PatientNotesEntity
import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.data.local.entity.PersonalReminderEntity
import com.neochildclinic.data.local.entity.ConsultationEntity
import com.neochildclinic.data.local.entity.VisitEntity
import kotlinx.coroutines.flow.Flow

interface PatientLocalDataSource {
    fun getAllPatients(): Flow<List<PatientEntity>>
    suspend fun getPatientById(id: String): PatientEntity?
    suspend fun insertPatient(patient: PatientEntity)
    suspend fun deletePatient(id: String, deletedAt: String, deletedBy: String)
    fun searchPatients(query: String): Flow<List<PatientEntity>>
    fun getPatientCount(): Flow<Int>
    fun getNotesForPatient(patientId: String): Flow<List<PatientNotesEntity>>
    suspend fun insertNote(note: PatientNotesEntity)
    suspend fun getPatientByClinicId(clinicId: String): PatientEntity?
    suspend fun isUnsyncedPatient(id: String): Boolean
    
    // For deletePatient cascade
    fun getVaccinationsForPatient(patientId: String): Flow<List<VisitEntity>>?
    fun getDueRemindersForPatient(patientId: String): Flow<List<ReminderEntity>>?
    fun getActivePersonalReminders(): Flow<List<PersonalReminderEntity>>?
    fun getConsultationsForPatient(patientId: String): Flow<List<ConsultationEntity>>?
    suspend fun deleteRemindersByPatientId(patientId: String, deletedAt: String, deletedBy: String)
    suspend fun deletePersonalReminder(id: String, deletedAt: String, deletedBy: String)
    suspend fun deleteConsultation(id: String, deletedAt: String, deletedBy: String)
}