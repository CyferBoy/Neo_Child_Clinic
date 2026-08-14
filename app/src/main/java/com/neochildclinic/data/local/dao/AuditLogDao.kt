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

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE timestamp >= :startTimestamp AND timestamp < :endTimestamp ORDER BY timestamp DESC")
    fun getLogsBetween(startTimestamp: String, endTimestamp: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE isSynced = 0")
    suspend fun getUnsyncedLogs(): List<AuditLogEntity>

    @Query("UPDATE audit_logs SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
