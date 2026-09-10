package com.neochildclinic.data.repository

import androidx.room.withTransaction
import com.neochildclinic.core.logger.AuditLogger
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.session.SessionManager
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.ExpenseEntity
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.data.local.entity.toEntity
import com.neochildclinic.domain.model.Expense
import com.neochildclinic.domain.repository.ExpenseRepository
import com.neochildclinic.domain.repository.SyncRepository
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val postgrest: Postgrest,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) : ExpenseRepository {

    private val expenseDao = database.expenseDao()
    private val syncQueueDao = database.syncQueueDao()

    override fun getAllExpenses(): Flow<List<Expense>> =
        expenseDao.getAllExpenses().map { list -> list.map { it.toDomain() } }

    override suspend fun getExpenseById(id: String): Expense? =
        expenseDao.getExpenseById(id)?.toDomain()

    override suspend fun addExpense(expense: Expense, user: String) {
        database.withTransaction {
            val userName = sessionManager.getCurrentUserName()
            val entity = expense.copy(createdBy = userName, updatedBy = userName)
                .toEntity(isSynced = false)
            expenseDao.insertExpense(entity)

            syncRepository.enqueue(
                entityName = "EXPENSE",
                entityId = entity.id,
                operation = SyncOperation.CREATE,
                priority = SyncPriority.MEDIUM
            )

            auditLogger.recordLog(
                module = "FINANCE",
                entityType = "EXPENSE",
                entityId = entity.id,
                action = "EXPENSE_ADDED",
                newValue = entity.amountPaise.toString(),
                remarks = "${entity.title} (${entity.category}) - ${entity.amountPaise / 100.0}"
            )
        }
    }

    override suspend fun updateExpense(expense: Expense, user: String) {
        database.withTransaction {
            val existing = expenseDao.getExpenseById(expense.id)
            val userName = sessionManager.getCurrentUserName()
            val entity = expense.copy(
                createdBy = existing?.createdBy ?: userName,
                createdAt = existing?.createdAt,
                updatedBy = userName
            ).toEntity(isSynced = false)
            expenseDao.insertExpense(entity)

            syncRepository.enqueue(
                entityName = "EXPENSE",
                entityId = entity.id,
                operation = SyncOperation.UPDATE,
                priority = SyncPriority.MEDIUM
            )

            auditLogger.recordLog(
                module = "FINANCE",
                entityType = "EXPENSE",
                entityId = entity.id,
                action = "EXPENSE_UPDATED",
                oldValue = existing?.amountPaise?.toString(),
                newValue = entity.amountPaise.toString(),
                remarks = "${entity.title} (${entity.category})"
            )
        }
    }

    override suspend fun deleteExpense(id: String, user: String) {
        database.withTransaction {
            val existing = expenseDao.getExpenseById(id) ?: return@withTransaction
            val userName = sessionManager.getCurrentUserName()
            val updated = existing.copy(
                isDeleted = true,
                updatedBy = userName,
                updatedAt = PatientUtils.getCurrentIsoTimestamp(),
                isSynced = false
            )
            expenseDao.insertExpense(updated)

            // Soft delete re-syncs as an UPDATE (is_deleted flips to true), not a DELETE -
            // per task section 5/20, synchronized expense records are never physically
            // removed. See ExpenseRepository.deleteExpense doc.
            syncRepository.enqueue(
                entityName = "EXPENSE",
                entityId = id,
                operation = SyncOperation.UPDATE,
                priority = SyncPriority.MEDIUM
            )

            auditLogger.recordLog(
                module = "FINANCE",
                entityType = "EXPENSE",
                entityId = id,
                action = "EXPENSE_DELETED",
                remarks = "${existing.title} (${existing.category}) soft-deleted"
            )
        }
    }

    override suspend fun refreshExpenses() {
        withContext(Dispatchers.IO) {
            try {
                val remoteExpenses = postgrest.from("expenses").select().decodeList<ExpenseEntity>()
                database.withTransaction {
                    for (remote in remoteExpenses) {
                        if (!syncQueueDao.isUnsynced("EXPENSE", remote.id)) {
                            expenseDao.insertExpense(remote.copy(isSynced = true))
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ExpenseRepo", "Refresh failed", e)
            }
        }
    }

    override fun getExpenseCount(): Flow<Int> = expenseDao.getExpenseCount()

    override suspend fun getExpensesInDateRange(fromDate: String, toDate: String): List<Expense> =
        expenseDao.getExpensesInDateRange(fromDate, toDate).map { it.toDomain() }

    override suspend fun getFilteredExpensesPage(
        category: String?,
        paymentMethod: String?,
        fromDate: String?,
        toDate: String?,
        query: String?,
        sortBy: String,
        limit: Int,
        offset: Int
    ): List<Expense> {
        return expenseDao.getFilteredExpensesPage(
            category = category,
            paymentMethod = paymentMethod,
            fromDate = fromDate,
            toDate = toDate,
            query = query?.takeIf { it.isNotBlank() },
            sortBy = sortBy,
            limit = limit,
            offset = offset
        ).map { it.toDomain() }
    }
}
