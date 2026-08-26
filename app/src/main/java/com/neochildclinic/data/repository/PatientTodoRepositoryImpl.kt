package com.neochildclinic.data.repository

import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.data.local.dao.PatientTodoDao
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.ConsultationTodoEntity
import com.neochildclinic.data.local.entity.VaccinationTodoEntity
import com.neochildclinic.domain.repository.PatientTodoRepository
import io.github.jan.supabase.postgrest.Postgrest
import com.neochildclinic.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientTodoRepositoryImpl @Inject constructor(
    database: AppDatabase,
    private val syncRepository: SyncRepository,
    private val postgrest: Postgrest,
    private val sessionManager: com.neochildclinic.core.session.SessionManager
) : PatientTodoRepository {
    private val dao: PatientTodoDao = database.patientTodoDao()

    override suspend fun refresh() {
        val consultations = postgrest.from("consultation_todos").select().decodeList<ConsultationTodoEntity>()
        val vaccinations = postgrest.from("vaccination_todos").select().decodeList<VaccinationTodoEntity>()
        consultations.forEach { remote ->
            val local = dao.getConsultationTodoById(remote.id)
            if (local == null || local.isSynced) dao.insertConsultation(remote.copy(isSynced = true))
        }
        vaccinations.forEach { remote ->
            val local = dao.getVaccinationTodoById(remote.id)
            if (local == null || local.isSynced) dao.insertVaccination(remote.copy(isSynced = true))
        }
    }

    override fun getTodayConsultations(date: String): Flow<List<ConsultationTodoEntity>> = dao.getTodayConsultations(date)
    override fun getTodayVaccinations(date: String): Flow<List<VaccinationTodoEntity>> = dao.getTodayVaccinations(date)

    override fun getConsultationsByDateAndStatus(date: String, status: String): Flow<List<ConsultationTodoEntity>> = dao.getConsultationsByDateAndStatus(date, status)
    override fun getVaccinationsByDateAndStatus(date: String, status: String): Flow<List<VaccinationTodoEntity>> = dao.getVaccinationsByDateAndStatus(date, status)
    override fun getDatesWithData(start: String, end: String): Flow<List<String>> = dao.getDatesWithData(start, end)

    override suspend fun updateStatus(type: String, id: String, status: String) {
        val now = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
        if (type == "CONSULTATION_TODO") {
            dao.updateConsultationStatus(id, status, now)
        } else {
            dao.updateVaccinationStatus(id, status, now)
        }
        syncRepository.enqueue(type, id, com.neochildclinic.core.model.SyncOperation.UPDATE, com.neochildclinic.core.model.SyncPriority.MEDIUM)
    }

    override suspend fun addConsultation(todo: ConsultationTodoEntity) {
        dao.insertConsultation(todo)
        syncRepository.enqueue("CONSULTATION_TODO", todo.id, SyncOperation.CREATE, SyncPriority.MEDIUM)
    }

    override suspend fun addVaccination(todo: VaccinationTodoEntity) {
        dao.insertVaccination(todo)
        syncRepository.enqueue("VACCINATION_TODO", todo.id, SyncOperation.CREATE, SyncPriority.MEDIUM)
    }

    override suspend fun deleteConsultation(id: String) {
        dao.deleteConsultation(id)
        syncRepository.enqueue("CONSULTATION_TODO", id, SyncOperation.DELETE, SyncPriority.MEDIUM)
    }

    override suspend fun deleteVaccination(id: String) {
        dao.deleteVaccination(id)
        syncRepository.enqueue("VACCINATION_TODO", id, SyncOperation.DELETE, SyncPriority.MEDIUM)
    }
}
