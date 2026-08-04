package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Legacy support for Reminder audits.
 * Now queries the central audit_logs table.
 */
@Dao
interface ReminderAuditDao {
    @Query("SELECT * FROM audit_logs WHERE patientId = :patientId AND module = 'PATIENT' ORDER BY timestamp DESC")
    fun getAuditsForPatient(patientId: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE id = :id LIMIT 1")
    suspend fun getAuditById(id: Long): AuditLogEntity?

    @Query("UPDATE audit_logs SET patientId = :masterId WHERE patientId = :duplicateId")
    suspend fun updatePatientId(duplicateId: String, masterId: String)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudit(log: AuditLogEntity): Long
}
