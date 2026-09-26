package com.neochildclinic.data.repository

import com.neochildclinic.data.local.entity.AuditLogEntity
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote audit-log reads. Writes go through [com.neochildclinic.core.logger.AuditLogger];
 * these paged reads deliberately hit Supabase directly (the same remote-only rule the old
 * feature code followed) so the audit screens always reflect every device's logs.
 */
@Singleton
class AuditLogRepositoryImpl @Inject constructor(
    private val postgrest: Postgrest
) {
    suspend fun getPaged(patientId: String?, offset: Int, limit: Int): List<AuditLogEntity> {
        val from = offset.toLong()
        val to = (offset + limit - 1).toLong()
        return postgrest.from("audit_logs").select {
            if (patientId != null) filter { eq("patient_id", patientId) }
            order("timestamp", Order.DESCENDING)
            range(from, to)
        }.decodeList<AuditLogEntity>()
    }
}