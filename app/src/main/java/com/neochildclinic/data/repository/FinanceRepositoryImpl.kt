package com.neochildclinic.data.repository

import com.neochildclinic.data.local.database.AppDatabase
import androidx.room.withTransaction
import com.neochildclinic.data.local.dao.FinanceDao
import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.features.statistics.FinanceCalculator
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
                paymentMethod = when {
                    cashAmount > 0 && onlineAmount > 0 -> "MIXED"
                    cashAmount > 0 -> "CASH"
                    onlineAmount > 0 -> "ONLINE"
                    else -> "FREE"
                },
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

    override suspend fun updateIncomeForVisit(
        visitId: String,
        amount: Double,
        cashAmount: Double,
        onlineAmount: Double,
        remarks: String?,
        recordedBy: String,
        transactionGroupId: String?
    ) {
        database.withTransaction {
            val transactions = financeDao.getTransactionsByVisitId(visitId)
                .filter { it.type == "INCOME" && it.category == "VACCINATION" }

            // Keep a vaccination finance record even when the fee is zero so historical
            // vaccine COGS remains reportable. Never delete historical finance rows here.

            val existing = transactions.maxByOrNull { it.timestamp }
            if (existing == null) {
                val transaction = FinanceEntity(
                    type = "INCOME",
                    category = "VACCINATION",
                    amount = amount,
                    cashAmount = cashAmount,
                    onlineAmount = onlineAmount,
                    paymentMethod = when {
                        cashAmount > 0 && onlineAmount > 0 -> "MIXED"
                        cashAmount > 0 -> "CASH"
                        onlineAmount > 0 -> "ONLINE"
                        else -> "FREE"
                    },
                    patientId = null,
                    visitId = visitId,
                    remarks = remarks,
                    recordedBy = recordedBy,
                    isSynced = false
                )
                financeDao.insertTransaction(transaction)
                syncRepository.enqueue(entityName = "FINANCE", entityId = transaction.id, operation = SyncOperation.CREATE, priority = SyncPriority.MEDIUM, transactionGroupId = transactionGroupId)
                return@withTransaction
            }

            val existingTransaction = existing

            val paymentMethod = when {
                cashAmount > 0 && onlineAmount > 0 -> "MIXED"
                cashAmount > 0 -> "CASH"
                onlineAmount > 0 -> "ONLINE"
                else -> "FREE"
            }

            val snapshot = existingTransaction.remarks?.substringAfter("[COGS_SNAPSHOT:", missingDelimiterValue = "")?.substringBefore("]")?.toDoubleOrNull()
            val updatedRemarks = if (snapshot != null) existingTransaction.remarks else remarks
            val updated = existingTransaction.copy(
                amount = amount,
                cashAmount = cashAmount,
                onlineAmount = onlineAmount,
                paymentMethod = paymentMethod,
                patientId = existing.patientId,
                visitId = visitId,
                remarks = updatedRemarks,
                recordedBy = recordedBy,
                isSynced = false
            )

            financeDao.insertTransaction(updated)
            syncRepository.enqueue(
                entityName = "FINANCE",
                entityId = updated.id,
                operation = SyncOperation.UPDATE,
                priority = SyncPriority.MEDIUM,
                transactionGroupId = transactionGroupId
            )

            auditLogger.recordLog(
                module = "FINANCE",
                entityType = "TRANSACTION",
                entityId = updated.id,
                action = "INCOME_UPDATED",
                patientId = updated.patientId,
                newValue = amount.toString(),
                remarks = "Vaccination income updated for visit $visitId",
                transactionGroupId = transactionGroupId
            )
        }
    }


    override suspend fun migrateLegacyVaccinationCogs(vaccinations: List<Vaccination>) {
        database.withTransaction {
            val validById = vaccinations.associateBy { it.id }
            val transactions = financeDao.getAllTransactionsSnapshot()
            transactions
                .filter { it.type.equals("INCOME", true) && it.category.equals("VACCINATION", true) }
                .forEach { transaction ->
                    val visitId = transaction.visitId ?: return@forEach
                    if (transaction.remarks?.contains("[COGS_SNAPSHOT:") == true) return@forEach
                    val vaccination = validById[visitId] ?: return@forEach
                    val cogs = vaccination.items.sumOf { item ->
                        item.netRate.coerceAtLeast(0.0) * item.quantity.coerceAtLeast(0)
                    }
                    val snapshotRemarks = FinanceCalculator.buildVaccinationRemarks(
                        vaccination.items.joinToString(", ") { it.vaccineName },
                        cogs
                    )
                    val updatedRemarks = transaction.remarks?.let {
                        if (it.contains("[COGS_SNAPSHOT:") ) it else "$it $snapshotRemarks"
                    } ?: snapshotRemarks
                    val updated = transaction.copy(
                        remarks = updatedRemarks,
                        isSynced = false
                    )
                    financeDao.insertTransaction(updated)
                    syncRepository.enqueue(
                        entityName = "FINANCE",
                        entityId = updated.id,
                        operation = SyncOperation.UPDATE,
                        priority = SyncPriority.MEDIUM
                    )
                }
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
