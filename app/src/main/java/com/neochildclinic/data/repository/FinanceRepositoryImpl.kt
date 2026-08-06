package com.neochildclinic.data.repository

import com.neochildclinic.data.local.database.AppDatabase
import androidx.room.withTransaction
import com.neochildclinic.data.local.dao.FinanceDao
import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.logger.AuditLogger
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinanceRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val financeDao: FinanceDao,
    private val postgrest: Postgrest,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger
) : FinanceRepository {

    override fun getAllTransactions(): Flow<List<FinanceEntity>> {
        return financeDao.getAllTransactions()
    }

    override fun getTransactionsForPatient(patientId: String): Flow<List<FinanceEntity>> {
        return financeDao.getTransactionsForPatient(patientId)
    }

    override fun getDailyIncome(start: Long): Flow<Double?> {
        val isoStart = com.neochildclinic.core.utils.PatientUtils.formatDate(java.util.Date(start))
        return financeDao.getDailyIncome(isoStart)
    }

    override suspend fun recordIncome(
        amount: Double,
        cashAmount: Double,
        onlineAmount: Double,
        category: String,
        patientId: String?,
        visitId: String?,
        remarks: String?,
        recordedBy: String,
        transactionGroupId: String?
    ) {
        database.withTransaction {
            val transaction = FinanceEntity(
                type = "INCOME",
                category = category,
                amount = amount,
                cashAmount = cashAmount,
                onlineAmount = onlineAmount,
                paymentMethod = if (cashAmount > 0 && onlineAmount > 0) "MIXED" else if (cashAmount > 0) "CASH" else "ONLINE",
                patientId = patientId,
                visitId = visitId,
                remarks = remarks,
                recordedBy = recordedBy,
                isSynced = false
            )
            financeDao.insertTransaction(transaction)
            syncRepository.enqueue(
                entityName = "FINANCE",
                entityId = transaction.id,
                operation = SyncOperation.CREATE,
                priority = SyncPriority.MEDIUM,
                transactionGroupId = transactionGroupId
            )
            
            auditLogger.recordLog(
                module = "FINANCE",
                entityType = "TRANSACTION",
                entityId = transaction.id,
                action = "INCOME_RECORDED",
                patientId = patientId,
                newValue = amount.toString(),
                remarks = "Income of $amount recorded in $category",
                transactionGroupId = transactionGroupId
            )
        }
    }

    override suspend fun recordExpense(
        amount: Double,
        category: String,
        remarks: String?,
        recordedBy: String
    ) {
        database.withTransaction {
            val transaction = FinanceEntity(
                type = "EXPENSE",
                category = category,
                amount = amount,
                paymentMethod = "CASH",
                remarks = remarks,
                recordedBy = recordedBy,
                isSynced = false
            )
            financeDao.insertTransaction(transaction)
            syncRepository.enqueue(
                entityName = "FINANCE",
                entityId = transaction.id,
                operation = SyncOperation.CREATE,
                priority = SyncPriority.MEDIUM
            )

            auditLogger.recordLog(
                module = "FINANCE",
                entityType = "TRANSACTION",
                entityId = transaction.id,
                action = "EXPENSE_RECORDED",
                newValue = amount.toString(),
                remarks = "Expense of $amount recorded in $category"
            )
        }
    }

    override suspend fun refreshTransactions() {
        withContext(Dispatchers.IO) {
            try {
                val transactions = postgrest.from("finance_transactions").select().decodeList<FinanceEntity>()
                database.withTransaction {
                    for (remote in transactions) {
                        val local = financeDao.getTransactionById(remote.id)
                        if (local == null || local.isSynced) {
                            financeDao.insertTransaction(remote.copy(isSynced = true))
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FinanceRepo", "Refresh failed", e)
            }
        }
    }
}
