package com.neochildclinic.data.local.datasource

import com.neochildclinic.data.local.dao.PatientDao
import com.neochildclinic.data.local.dao.PatientNotesDao
import com.neochildclinic.data.local.dao.DueReminderDao
import com.neochildclinic.data.local.dao.PersonalReminderDao
import com.neochildclinic.data.local.dao.ConsultationDao
import com.neochildclinic.data.local.dao.VaccinationDao
import com.neochildclinic.data.local.dao.SyncQueueDao
import com.neochildclinic.data.local.entity.PatientEntity
import com.neochildclinic.data.local.entity.PatientNotesEntity
import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.data.local.entity.PersonalReminderEntity
import com.neochildclinic.data.local.entity.ConsultationEntity
import com.neochildclinic.data.local.entity.VisitEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientLocalDataSourceImpl @Inject constructor(
    private val patientDao: PatientDao,
    private val notesDao: PatientNotesDao,
    private val dueReminderDao: DueReminderDao,
    private val personalReminderDao: PersonalReminderDao,
    private val consultationDao: ConsultationDao,
    private val vaccinationDao: VaccinationDao,
    private val syncQueueDao: SyncQueueDao
) : PatientLocalDataSource {

    override fun getAllPatients(): Flow<List<PatientEntity>> = patientDao.getAllPatients()

    override suspend fun getPatientById(id: String): PatientEntity? = patientDao.getPatientById(id)

    override suspend fun insertPatient(patient: PatientEntity) = patientDao.insertPatient(patient)

    override suspend fun deletePatient(id: String, deletedAt: String, deletedBy: String) {
        patientDao.deletePatient(id, deletedAt, deletedBy)
    }

    override fun searchPatients(query: String): Flow<List<PatientEntity>> = patientDao.searchPatients(query)

    override fun getPatientCount(): Flow<Int> = patientDao.getPatientCount()

    override fun getNotesForPatient(patientId: String): Flow<List<PatientNotesEntity>> = notesDao.getNotesForPatient(patientId)

    override suspend fun insertNote(note: PatientNotesEntity) = notesDao.insertNote(note)

    override suspend fun getPatientByClinicId(clinicId: String): PatientEntity? = patientDao.getPatientByClinicId(clinicId)

    override suspend fun isUnsyncedPatient(id: String): Boolean = syncQueueDao.isUnsynced("PATIENT", id)

    override fun getVaccinationsForPatient(patientId: String): Flow<List<VisitEntity>>? =
        vaccinationDao.getVaccinationsForPatient(patientId)

    override fun getDueRemindersForPatient(patientId: String): Flow<List<ReminderEntity>>? =
        dueReminderDao.getDueRemindersForPatient(patientId)

    override fun getActivePersonalReminders(): Flow<List<PersonalReminderEntity>>? =
        personalReminderDao.getActiveReminders()

    override fun getConsultationsForPatient(patientId: String): Flow<List<ConsultationEntity>>? =
        consultationDao.getConsultationsForPatient(patientId)

    override suspend fun deleteRemindersByPatientId(patientId: String, deletedAt: String, deletedBy: String) =
        dueReminderDao.deleteRemindersByPatientId(patientId, deletedAt, deletedBy)

    override suspend fun deletePersonalReminder(id: String, deletedAt: String, deletedBy: String) =
        personalReminderDao.delete(id, deletedAt, deletedBy)

    override suspend fun deleteConsultation(id: String, deletedAt: String, deletedBy: String) =
        consultationDao.deleteConsultation(id, deletedAt, deletedBy)
}