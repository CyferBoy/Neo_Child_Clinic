package com.neochildclinic.feature.audit.data

import com.neochildclinic.core.logger.AuditLogWriter
import com.neochildclinic.data.local.dao.AuditLogDao
import com.neochildclinic.data.local.entity.AuditLogEntity
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.domain.model.AuditLog
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.feature.audit.domain.repository.AuditLogRepository
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote audit-log reads and local audit-log persistence.
 */
@Singleton
class AuditLogRepositoryImpl @Inject constructor(
    private val postgrest: Postgrest,
    private val auditLogDao: AuditLogDao,
    private val syncRepository: SyncRepository
) : AuditLogRepository, AuditLogWriter {

    override suspend fun getPaged(patientId: String?, offset: Int, limit: Int): List<AuditLog> {
        val from = offset.toLong()
        val to = (offset + limit - 1).toLong()
        return postgrest.from("audit_logs").select {
            if (patientId != null) filter { eq("patient_id", patientId) }
            order("timestamp", Order.DESCENDING)
            range(from, to)
        }.decodeList<AuditLogEntity>().map { it.toDomain() }
    }

    override suspend fun recordLog(auditLog: AuditLog, transactionGroupId: String?) {
        val entity = AuditLogEntity(
            id = auditLog.id,
            timestamp = auditLog.timestamp,
            user = auditLog.user,
            module = auditLog.module,
            entityType = auditLog.entityType,
            entityId = auditLog.entityId,
            action = auditLog.action,
            oldValue = auditLog.oldValue,
            newValue = auditLog.newValue,
            remarks = auditLog.remarks,
            device = auditLog.device,
            patientId = auditLog.patientId,
            isSynced = false
        )
        auditLogDao.insertLog(entity)
        syncRepository.enqueue(
            entityName = "AUDIT_LOG",
            entityId = entity.id,
            operation = SyncOperation.CREATE,
            priority = SyncPriority.LOW,
            transactionGroupId = transactionGroupId
        )
    }
}