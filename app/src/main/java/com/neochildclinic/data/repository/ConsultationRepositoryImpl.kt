package com.neochildclinic.data.repository

import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.data.local.entity.toEntity
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.domain.repository.ConsultationRepository
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.logger.AuditLogger
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConsultationRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val postgrest: Postgrest,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger
) : ConsultationRepository {

    private val consultationDao = database.consultationDao()
    private val syncQueueDao = database.syncQueueDao()

    override fun getConsultationsForPatient(patientId: String): Flow<List<Consultation>> =
        consultationDao.getConsultationsForPatient(patientId).map { list -> list.map { it.toDomain() } }

    override suspend fun getConsultationById(id: String): Consultation? =
        consultationDao.getConsultationById(id)?.toDomain()

    override suspend fun addConsultation(consultation: Consultation) {
        val entity = consultation.toEntity(isSynced = false)
        consultationDao.insertConsultation(entity)
        
        syncRepository.enqueue(
            entityName = "CONSULTATION",
            entityId = consultation.id,
            operation = SyncOperation.CREATE,
            priority = SyncPriority.MEDIUM
        )

        auditLogger.recordLog(
            module = "PATIENT",
            entityType = "CONSULTATION",
            entityId = consultation.id,
            action = "CONSULTATION",
            patientId = consultation.patientId,
            remarks = "Consultation recorded: ₹${consultation.amount}"
        )
    }

    override suspend fun deleteConsultation(id: String) {
        val existing = consultationDao.getConsultationById(id) ?: return
        consultationDao.deleteConsultation(id)
        
        syncRepository.enqueue(
            entityName = "CONSULTATION",
            entityId = id,
            operation = SyncOperation.DELETE,
            priority = SyncPriority.MEDIUM
        )

        auditLogger.recordLog(
            module = "PATIENT",
            entityType = "CONSULTATION",
            entityId = id,
            action = "DELETED",
            patientId = existing.patientId,
            remarks = "Consultation deleted"
        )
    }

    override suspend fun refreshConsultations() {
        withContext(Dispatchers.IO) {
            try {
                val remoteConsultations = postgrest.from("consultations").select().decodeList<Consultation>()
                for (remote in remoteConsultations) {
                    if (!syncQueueDao.isUnsynced("CONSULTATION", remote.id)) {
                        consultationDao.insertConsultation(remote.toEntity(isSynced = true))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ConsultationRepo", "Refresh failed", e)
            }
        }
    }
}
