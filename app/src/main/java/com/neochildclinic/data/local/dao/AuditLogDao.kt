package com.neochildclinic.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.neochildclinic.data.local.entity.AuditLogEntity

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insertLog(log: AuditLogEntity)

    @Query("SELECT * FROM audit_logs WHERE id = :id")
    suspend fun getLogById(id: String): AuditLogEntity?

    /**
     * Drops synced local audit rows past the retention window. The server keeps one year;
     * the local table exists for offline/sync and reminder timelines.
     */
    @Query(
        """
        DELETE FROM audit_logs
        WHERE isSynced = 1 AND timestamp < :cutoff
          AND NOT EXISTS (
              SELECT 1 FROM sync_queue
              WHERE sync_queue.entityId = audit_logs.id
                AND sync_queue.entityName = 'AUDIT_LOG'
          )
        """
    )
    suspend fun deleteSyncedOlderThan(cutoff: String)
}
