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

    @Query("SELECT * FROM borrow_returns WHERE borrow_record_id = :borrowRecordId ORDER BY returned_date DESC, created_at DESC")
    fun getReturnsForRecord(borrowRecordId: String): Flow<List<BorrowReturnEntity>>

    @Query("SELECT * FROM borrow_returns")
    fun getAllReturns(): Flow<List<BorrowReturnEntity>>

    @Query("SELECT * FROM borrow_returns WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BorrowReturnEntity?

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM borrow_returns WHERE borrow_record_id = :borrowRecordId")
    suspend fun getTotalReturnedQuantity(borrowRecordId: String): Int
}
