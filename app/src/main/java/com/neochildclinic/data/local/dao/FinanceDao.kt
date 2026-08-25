package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.FinanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: FinanceEntity)

    @Query("SELECT * FROM finance_transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): FinanceEntity?

    @Query("SELECT * FROM finance_transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactionsSnapshot(): List<FinanceEntity>

    @Query("SELECT * FROM finance_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<FinanceEntity>>

    @Query("SELECT * FROM finance_transactions WHERE patientId = :patientId ORDER BY timestamp DESC")
    fun getTransactionsForPatient(patientId: String): Flow<List<FinanceEntity>>

    @Query("SELECT SUM(amount) FROM finance_transactions WHERE type = 'INCOME' AND timestamp >= :start")
    fun getDailyIncome(start: String): Flow<Double?>

    @Query("SELECT * FROM finance_transactions WHERE visitId = :visitId")
    suspend fun getTransactionsByVisitId(visitId: String): List<FinanceEntity>

    @Query("DELETE FROM finance_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)


    @Query("UPDATE finance_transactions SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
