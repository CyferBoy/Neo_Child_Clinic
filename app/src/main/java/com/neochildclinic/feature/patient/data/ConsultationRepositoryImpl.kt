package com.neochildclinic.feature.patient.data
import com.neochildclinic.core.database.TransactionRunner
import com.neochildclinic.data.local.dao.ConsultationDao
import com.neochildclinic.data.local.dao.VaccinationDao
import com.neochildclinic.data.local.dao.FinanceDao
import com.neochildclinic.data.local.dao.SyncQueueDao
import com.neochildclinic.feature.consultation.domain.repository.ConsultationRepository

import com.neochildclinic.data.local.entity.*
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.core.logger.AuditLogger
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.neochildclinic.core.sync.cloudRefresh

@Singleton
class ConsultationRepositoryImpl @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val syncQueueDao: SyncQueueDao,
    private val financeDao: FinanceDao,
    private val vaccinationDao: VaccinationDao,
    private val consultationDao: ConsultationDao,
    private val postgrest: Postgrest,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: com.neochildclinic.core.security.SessionManager
) : ConsultationRepository {

    override fun getConsultationsForPatient(patientId: String): Flow<List<Consultation>> =
        consultationDao.getConsultationsForPatient(patientId).map { list -> list.map { it.toDomain() } }

    override suspend fun getConsultationById(id: String): Consultation? =
        consultationDao.getConsultationById(id)?.toDomain()

    override suspend fun addConsultation(consultation: Consultation, transactionGroupId: String? ) {
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

    override suspend fun updateConsultation(consultation: Consultation, transactionGroupId: String? ) {
        transactionRunner.run {
            val existing = consultationDao.getConsultationById(consultation.id)
                ?: throw IllegalArgumentException("Consultation not found")

            val now = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp()
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
        val userName = sessionManager.getCurrentUserName()
        val now = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp()

        transactionRunner.run {
            val existing = consultationDao.getConsultationById(id) ?: return@run

            // 1. Soft-Delete Consultation (Child)
            consultationDao.deleteConsultation(id)
            syncRepository.enqueue(
                entityName = "CONSULTATION",
                entityId = id,
                operation = SyncOperation.UPDATE,
                priority = SyncPriority.MEDIUM
            )

            // 2. Soft-Delete associated finance transactions for the visit
            if (existing.visitId.isNotBlank()) {
                val visitFinanceTxns = financeDao.getTransactionsByVisitId(existing.visitId)
                for (txn in visitFinanceTxns) {
                    financeDao.deleteTransactionById(txn.id)
                    syncRepository.enqueue(
                        entityName = "FINANCE",
                        entityId = txn.id,
                        operation = SyncOperation.UPDATE,
                        priority = SyncPriority.MEDIUM
                    )
                }
            }

            // 3. Soft-Delete Visit Header (Mother)
            if (existing.visitId.isNotBlank()) {
                vaccinationDao.deleteVaccination(existing.visitId)
                syncRepository.enqueue(
                    entityName = "VISIT",
                    entityId = existing.visitId,
                    operation = SyncOperation.UPDATE,
                    priority = SyncPriority.MEDIUM
                )
            }

            auditLogger.recordLog(
                module = "PATIENT",
                entityType = "CONSULTATION",
                entityId = id,
                action = "SOFT_DELETED",
                patientId = existing.patientId,
                remarks = "Consultation and associated visit header soft deleted"
            )
        }
    }

    override suspend fun refreshConsultations() = cloudRefresh("ConsultationRepo") {
        val entities = postgrest.from("consultations").select { filter { eq("is_deleted", false) } }.decodeList<ConsultationEntity>()
        transactionRunner.run {
            for (remote in entities) {
                if (!syncQueueDao.isUnsynced("CONSULTATION", remote.id)) {
                    consultationDao.insertConsultation(remote.copy(isSynced = true))
                }
            }
        }
    }
}
