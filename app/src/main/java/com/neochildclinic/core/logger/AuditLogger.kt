package com.neochildclinic.core.logger

import android.os.Build
import com.neochildclinic.core.common.DateUtils
import com.neochildclinic.core.security.SessionManager
import com.neochildclinic.domain.model.AuditLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogger @Inject constructor(
    private val sessionManager: SessionManager,
    private val auditLogWriter: AuditLogWriter
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Centralized logging for all business modules.
     *
     * ⚠️ Fire-and-forget: this writes on its own scope and does **not** join the caller's
     * transaction. Called inside `transactionRunner.run`, the audit row is committed
     * independently, so it survives a rollback and records an action that never happened —
     * and on a patient merge, claims two records were merged when they were not (NCC-022).
     * Inside a transaction, use [recordLog].
     */
    fun log(
        module: String,
        entityType: String,
        entityId: String,
        action: String,
        patientId: String? = null,
        oldValue: String? = null,
        newValue: String? = null,
        remarks: String? = null,
        transactionGroupId: String? = null
    ) {
        scope.launch {
            recordLog(module, entityType, entityId, action, patientId, oldValue, newValue, remarks, transactionGroupId)
        }
    }

    /**
     * Suspendable version for use inside transactions.
     */
    suspend fun recordLog(
        module: String,
        entityType: String,
        entityId: String,
        action: String,
        patientId: String? = null,
        oldValue: String? = null,
        newValue: String? = null,
        remarks: String? = null,
        transactionGroupId: String? = null
    ) {
        try {
            val userLabel = sessionManager.getCurrentUserName()
            val timestamp = DateUtils.getCurrentIsoTimestamp()
            val device = "${Build.MANUFACTURER} ${Build.MODEL}"

            val log = AuditLog(
                timestamp = timestamp,
                user = userLabel,
                module = module,
                entityType = entityType,
                entityId = entityId,
                action = action,
                oldValue = oldValue,
                newValue = newValue,
                remarks = remarks,
                device = device,
                patientId = patientId
            )

            auditLogWriter.recordLog(log, transactionGroupId)
        } catch (e: Exception) {
            android.util.Log.e("AuditLogger", "Audit log failed: ${e.message}")
        }
    }
}
