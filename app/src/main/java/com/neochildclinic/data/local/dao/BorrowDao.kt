package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.BorrowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BorrowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: BorrowEntity)

    @Query("SELECT * FROM borrow_records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: String): BorrowEntity?

    @Query("SELECT * FROM borrow_records WHERE vaccineId = :vaccineId")
    fun getRecordsForVaccine(vaccineId: String): Flow<List<BorrowEntity>>

    @Query("SELECT * FROM borrow_records WHERE isReturned = 0")
    fun getActiveBorrows(): Flow<List<BorrowEntity>>

    @Query("SELECT * FROM borrow_records WHERE isReturned = 1")
    fun getReturnedBorrows(): Flow<List<BorrowEntity>>

    // --- Pagination (large-data scalability pass) --- additive, existing Flow methods
    // above are untouched. isReturned has no dedicated index today; at clinic scale this
    // table is small relative to patients/visits, so id-ordered paging is used rather
    // than adding a new index purely for this.
    @Query("SELECT * FROM borrow_records WHERE isReturned = 0 ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun getActiveBorrowsPage(limit: Int, offset: Int): List<BorrowEntity>

    @Query("SELECT * FROM borrow_records WHERE isReturned = 1 ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun getReturnedBorrowsPage(limit: Int, offset: Int): List<BorrowEntity>

    @Query("UPDATE borrow_records SET isReturned = 1, returnedDate = :date, isSynced = 0 WHERE id = :id")
    suspend fun markReturned(id: String, date: String)

    @Query("DELETE FROM borrow_records WHERE id = :id")
    suspend fun deleteById(id: String)
}
