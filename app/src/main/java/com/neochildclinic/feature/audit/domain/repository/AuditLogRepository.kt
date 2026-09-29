package com.neochildclinic.feature.audit.domain.repository

import com.neochildclinic.domain.model.AuditLog

/**
 * Remote audit-log reads. Writes go through AuditLogger; these paged reads deliberately hit
 * Supabase directly so the audit screens always reflect every device's logs.
 */
interface AuditLogRepository {
    suspend fun getPaged(patientId: String?, offset: Int, limit: Int): List<AuditLog>
}