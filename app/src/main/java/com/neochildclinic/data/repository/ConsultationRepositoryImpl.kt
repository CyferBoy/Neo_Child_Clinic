package com.neochildclinic.data.repository

import com.neochildclinic.data.local.database.AppDatabase
import androidx.room.withTransaction
import com.neochildclinic.data.local.entity.*
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.domain.repository.ConsultationRepository
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.logger.AuditLogger
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.encodeToString
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
    private val financeRepository: com.neochildclinic.domain.repository.FinanceRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: com.neochildclinic.core.session.SessionManager
) : ConsultationRepository {

    private val consultationDao = database.consultationDao()
    private val vaccinationDao = database.vaccinationDao()
    private val syncQueueDao = database.syncQueueDao()

    override fun getConsultationsForPatient(patientId: String): Flow<List<Consultation>> =
        consultationDao.getConsultationsForPatient(patientId).map { list -> list.map { it.toDomain() } }

    override suspend fun getConsultationById(id: String): Consultation? =
        consultationDao.getConsultationById(id)?.toDomain()

    override suspend fun addConsultation(consultation: Consultation, transactionGroupId: String?) {
        val userName = sessionManager.getCurrentUserName()
        val entity = consultation.copy(
            createdBy = userName,
            updatedBy = userName
        ).toEntity(isSynced = false)
        consultationDao.insertConsultation(entity)
        
        syncRepository.enqueue(
            entityName = "CONSULTATION",
            entityId = consultation.id,
            operation = SyncOperation.CREATE,
            priority = SyncPriority.MEDIUM,
            transactionGroupId = transactionGroupId
        )

        auditLogger.recordLog(
            module = "PATIENT",
            entityType = "CONSULTATION",
            entityId = consultation.id,
            action = "CONSULTATION",
            patientId = consultation.patientId,
            remarks = "Consultation recorded: ₹${consultation.amount}",
            transactionGroupId = transactionGroupId
        )
    }

    override suspend fun updateConsultation(consultation: Consultation, transactionGroupId: String?) {
        database.withTransaction {
            val existing = consultationDao.getConsultationById(consultation.id)
                ?: throw IllegalArgumentException("Consultation not found")

            val now = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
            val userName = sessionManager.getCurrentUserName()
            val visitId = if (consultation.visitId.isBlank()) existing.visitId else consultation.visitId
            val updatedEntity = consultation.copy(
                visitId = visitId,
                // Creation time belongs to the original persisted record and must never
                // be replaced by the edit request.
                createdAt = existing.createdAt ?: consultation.createdAt,
                updatedAt = now,
                createdBy = existing.createdBy ?: consultation.createdBy ?: userName,
                updatedBy = userName
            ).toEntity(isSynced = false)

            consultationDao.insertConsultation(updatedEntity)

            // Keep the visit header in sync only when fields mirrored to the visit
            // actually changed. A consultation-only edit should not create an
            // unnecessary VISIT UPDATE/sync operation.
            if (updatedEntity.visitId.isNotBlank()) {
                val visit = vaccinationDao.getVaccinationById(updatedEntity.visitId)
                if (visit != null) {
                    val visitChanged =
                        visit.dateGiven != updatedEntity.date ||
                        visit.doctorId != updatedEntity.doctorId ||
                        visit.doctor != updatedEntity.doctorName ||
                        visit.notes != updatedEntity.problem ||
                        visit.cashAmount != updatedEntity.cashAmount ||
                        visit.onlineAmount != updatedEntity.onlineAmount ||
                        visit.totalPaid != updatedEntity.amount

                    if (visitChanged) {
                        vaccinationDao.insertVaccination(
                            visit.copy(
                                dateGiven = updatedEntity.date,
                                doctorId = updatedEntity.doctorId,
                                doctor = updatedEntity.doctorName,
                                notes = updatedEntity.problem,
                                cashAmount = updatedEntity.cashAmount,
                                onlineAmount = updatedEntity.onlineAmount,
                                totalPaid = updatedEntity.amount,
                                updatedAt = now,
                                isSynced = false
                            )
                        )
                        syncRepository.enqueue(
                            entityName = "VISIT",
                            entityId = updatedEntity.visitId,
                            operation = SyncOperation.UPDATE,
                            priority = SyncPriority.HIGH,
                            transactionGroupId = transactionGroupId
                        )
                    }
                }
            }

            syncRepository.enqueue(
                entityName = "CONSULTATION",
                entityId = updatedEntity.id,
                operation = SyncOperation.UPDATE,
                priority = SyncPriority.MEDIUM,
                transactionGroupId = transactionGroupId
            )

            auditLogger.recordLog(
                module = "PATIENT",
                entityType = "CONSULTATION",
                entityId = updatedEntity.id,
                action = "CONSULTATION_UPDATED",
                patientId = updatedEntity.patientId,
                oldValue = kotlinx.serialization.json.Json.encodeToString(existing.toDomain()),
                newValue = kotlinx.serialization.json.Json.encodeToString(updatedEntity.toDomain()),
                remarks = "Consultation updated",
                transactionGroupId = transactionGroupId
            )
        }
    }

    override suspend fun deleteConsultation(id: String) {
        database.withTransaction {
            val existing = consultationDao.getConsultationById(id) ?: return@withTransaction
            
            // Financial transactions are historical records and must remain after a clinical record is deleted.
            // 1. Delete Consultation (Child)
            consultationDao.deleteConsultation(id)
            syncRepository.enqueue(
                entityName = "CONSULTATION",
                entityId = id,
                operation = SyncOperation.DELETE,
                priority = SyncPriority.MEDIUM
            )

            // 3. Delete Visit Header (Mother)
            if (existing.visitId.isNotBlank()) {
                vaccinationDao.deleteVaccination(existing.visitId)
                syncRepository.enqueue(
                    entityName = "VISIT",
                    entityId = existing.visitId,
                    operation = SyncOperation.DELETE,
                    priority = SyncPriority.MEDIUM
                )
            }

            auditLogger.recordLog(
                module = "PATIENT",
                entityType = "CONSULTATION",
                entityId = id,
                action = "DELETED",
                patientId = existing.patientId,
                remarks = "Consultation and associated visit header deleted"
            )
        }
    }

    override suspend fun refreshConsultations() {
        withContext(Dispatchers.IO) {
            try {
                val entities = postgrest.from("consultations").select().decodeList<ConsultationEntity>()
                for (remote in entities) {
                    if (!syncQueueDao.isUnsynced("CONSULTATION", remote.id)) {
                        consultationDao.insertConsultation(remote.copy(isSynced = true))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ConsultationRepo", "Refresh failed", e)
            }
        }
    }
}
