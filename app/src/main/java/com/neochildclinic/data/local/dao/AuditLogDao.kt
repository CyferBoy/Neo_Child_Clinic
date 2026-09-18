package com.neochildclinic.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.neochildclinic.data.local.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insertLog(log: AuditLogEntity)

    @Query("SELECT * FROM audit_logs WHERE id = :id")
    suspend fun getLogById(id: String): AuditLogEntity?

    @Query("SELECT * FROM audit_logs WHERE patientId = :patientId ORDER BY timestamp DESC")
    fun getLogsForPatient(patientId: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE entityType = :type AND entityId = :id ORDER BY timestamp DESC")
    fun getLogsForEntity(type: String, id: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE module = :module ORDER BY timestamp DESC")
    fun getLogsForModule(module: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllLogs(): Flow<List<AuditLogEntity>>

    // --- Pagination (large-data scalability pass) ---
    // getAllLogs() above already caps at 200; these cover the three per-patient/per-entity/
    // per-module queries, which had no cap at all and are indexed on patientId, entityType+
    // entityId, and module respectively (see AuditLogEntity). Additive, existing Flow
    // methods above are untouched.
    @Query("SELECT * FROM audit_logs WHERE patientId = :patientId ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getLogsForPatientPage(patientId: String, limit: Int, offset: Int): List<AuditLogEntity>

    @Query("SELECT * FROM audit_logs WHERE entityType = :type AND entityId = :id ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getLogsForEntityPage(type: String, id: String, limit: Int, offset: Int): List<AuditLogEntity>

    @Query("SELECT * FROM audit_logs WHERE module = :module ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getLogsForModulePage(module: String, limit: Int, offset: Int): List<AuditLogEntity>

    // Keyset variant for scrolling past the newest 200 shown by getAllLogs(): pass the
    // timestamp of the last row already shown (id as tiebreak for identical timestamps).
    @Query(
        """
        SELECT * FROM audit_logs
        WHERE (timestamp < :lastTimestamp) OR (timestamp = :lastTimestamp AND id < :lastId)
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun getLogsBefore(lastTimestamp: String, lastId: String, limit: Int): List<AuditLogEntity>
}
