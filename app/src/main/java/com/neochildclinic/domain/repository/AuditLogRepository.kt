package com.neochildclinic.domain.repository

import com.neochildclinic.data.local.entity.AuditLogEntity

/**
 * Remote audit-log reads. Writes go through AuditLogger; these paged reads deliberately hit
 * Supabase directly so the audit screens always reflect every device's logs.
 */
interface AuditLogRepository {
    suspend fun getPaged(patientId: String?, offset: Int, limit: Int): List<AuditLogEntity>
}