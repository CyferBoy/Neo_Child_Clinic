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
     *
     * SE-02: the actor is captured synchronously from the authoritative session user id;
     * see recordLog's actor parameter for what this still cannot guarantee.
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
        // SE-02: this runs now, on the caller's thread. If we waited for the launch below,
        // a sign-out/switch in between would attribute the row to the wrong account.
        val actor = sessionManager.getCurrentUserId()?.let { id ->
            "${sessionManager.getCurrentUserDisplayName()} [$id]"
        }
        scope.launch {
            recordLog(module, entityType, entityId, action, patientId, oldValue, newValue, remarks, transactionGroupId, actor)
        }
    }

    /**
     * Suspendable version for use inside transactions.
     *
     * @param actor Actor captured from the session at call time by [log]. Remaining
     * constraint: it is still client-supplied from the session — audit_logs has no
     * server-populated actor column, so a compromised client can forge the display name.
     * Server-side attribution needs a schema migration and is out of scope here.
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
        transactionGroupId: String? = null,
        actor: String? = null
    ) {
        try {
            val userLabel = actor ?: sessionManager.getCurrentUserName()
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
