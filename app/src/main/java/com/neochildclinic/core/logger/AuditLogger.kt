package com.neochildclinic.core.logger

import android.os.Build
import com.neochildclinic.data.local.dao.AuditLogDao
import com.neochildclinic.data.local.entity.AuditLogEntity
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogger @Inject constructor(
    private val auth: Auth,
    private val auditLogDao: AuditLogDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Centralized logging for all business modules.
     */
    fun log(
        module: String,
        entityType: String,
        entityId: String,
        action: String,
        patientId: String? = null,
        oldValue: String? = null,
        newValue: String? = null,
        remarks: String? = null
    ) {
        scope.launch {
            recordLog(module, entityType, entityId, action, patientId, oldValue, newValue, remarks)
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
        remarks: String? = null
    ) {
        val user = auth.currentSessionOrNull()?.user
        val userEmail = user?.email ?: "Unknown"
        val timestamp = System.currentTimeMillis()
        val device = "${Build.MANUFACTURER} ${Build.MODEL}"

        val logEntity = AuditLogEntity(
            timestamp = timestamp,
            user = userEmail,
            module = module,
            entityType = entityType,
            entityId = entityId,
            action = action,
            oldValue = oldValue,
            newValue = newValue,
            remarks = remarks,
            device = device,
            patientId = patientId,
            isSynced = false
        )

        // 1. Local Log (Blocking in suspend context)
        // SyncRepository will handle uploading this to Supabase automatically
        auditLogDao.insertLog(logEntity)
    }

    /**
     * Legacy support mapper.
     */
    fun logAction(action: String, patientId: String?, details: String = "") {
        log(
            module = "LEGACY",
            entityType = "UNKNOWN",
            entityId = "0",
            action = action,
            patientId = patientId,
            remarks = details
        )
    }
}
