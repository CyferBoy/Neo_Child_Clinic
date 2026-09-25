package com.neochildclinic.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neochildclinic.data.local.entity.BorrowReturnEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BorrowReturnDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: BorrowReturnEntity)

    @Query("SELECT * FROM borrow_returns WHERE borrow_record_id = :borrowRecordId AND is_deleted = 0 ORDER BY returned_date DESC, created_at DESC")
    fun getReturnsForRecord(borrowRecordId: String): Flow<List<BorrowReturnEntity>>

    @Query("SELECT * FROM borrow_returns WHERE is_deleted = 0")
    fun getAllReturns(): Flow<List<BorrowReturnEntity>>

    @Query("SELECT * FROM borrow_returns WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getById(id: String): BorrowReturnEntity?

    @Query("UPDATE borrow_returns SET is_deleted = 1, deleted_at = :deletedAt, deleted_by = :deletedBy, is_synced = 0, updated_by = :deletedBy WHERE id = :id AND is_deleted = 0")
    suspend fun deleteById(id: String, deletedAt: String, deletedBy: String?)
}
