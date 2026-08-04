package com.neochildclinic.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.neochildclinic.data.local.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insertLog(log: AuditLogEntity): Long

    @Query("SELECT * FROM audit_logs WHERE id = :id")
    suspend fun getLogById(id: Long): AuditLogEntity?

    @Query("SELECT * FROM audit_logs WHERE patientId = :patientId ORDER BY timestamp DESC")
    fun getLogsForPatient(patientId: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE entityType = :type AND entityId = :id ORDER BY timestamp DESC")
    fun getLogsForEntity(type: String, id: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE module = :module ORDER BY timestamp DESC")
    fun getLogsForModule(module: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE isSynced = 0")
    suspend fun getUnsyncedLogs(): List<AuditLogEntity>

    @Query("UPDATE audit_logs SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)
}
