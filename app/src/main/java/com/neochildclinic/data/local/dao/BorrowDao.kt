package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.BorrowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BorrowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: BorrowEntity)

    @Query("SELECT * FROM borrow_records WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getRecordById(id: String): BorrowEntity?

    @Query("SELECT * FROM borrow_records WHERE vaccineId = :vaccineId AND is_deleted = 0")
    fun getRecordsForVaccine(vaccineId: String): Flow<List<BorrowEntity>>

    @Query("SELECT * FROM borrow_records WHERE isReturned = 0 AND is_deleted = 0")
    fun getActiveBorrows(): Flow<List<BorrowEntity>>

    @Query("SELECT * FROM borrow_records WHERE isReturned = 1 AND is_deleted = 0")
    fun getReturnedBorrows(): Flow<List<BorrowEntity>>

    @Query("UPDATE borrow_records SET isReturned = 1, returnedDate = :date, isSynced = 0 WHERE id = :id AND is_deleted = 0")
    suspend fun markReturned(id: String, date: String)

    @Query("UPDATE borrow_records SET is_deleted = 1, deleted_at = :deletedAt, deleted_by = :deletedBy, isSynced = 0, updated_by = :deletedBy WHERE id = :id AND is_deleted = 0")
    suspend fun deleteById(id: String, deletedAt: String, deletedBy: String?)
}
