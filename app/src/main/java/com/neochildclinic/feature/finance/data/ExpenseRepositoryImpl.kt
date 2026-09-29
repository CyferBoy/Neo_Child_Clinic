package com.neochildclinic.feature.finance.data
import com.neochildclinic.core.database.TransactionRunner
import com.neochildclinic.data.local.dao.ExpenseDao
import com.neochildclinic.data.local.dao.SyncQueueDao
import com.neochildclinic.feature.finance.domain.repository.ExpenseRepository

import com.neochildclinic.core.logger.AuditLogger
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.core.security.SessionManager
import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.data.local.entity.ExpenseEntity
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.data.local.entity.toEntity
import com.neochildclinic.domain.model.Expense
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.neochildclinic.core.sync.cloudRefresh

@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val syncQueueDao: SyncQueueDao,
    private val expenseDao: ExpenseDao,
    private val postgrest: Postgrest,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) : ExpenseRepository {

    override fun getAllExpenses(): Flow<List<Expense>> =
        expenseDao.getAllExpenses().map { list -> list.map { it.toDomain() } }

    override suspend fun getExpenseById(id: String): Expense? =
        expenseDao.getExpenseById(id)?.toDomain()

    override suspend fun addExpense(expense: Expense, user: String) {
        transactionRunner.run {
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
        transactionRunner.run {
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
        val now = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp()
        transactionRunner.run {
            val existing = expenseDao.getExpenseById(id) ?: return@run
            expenseDao.deleteExpense(id)

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
                action = "EXPENSE_SOFT_DELETED",
                remarks = "${existing.title} (${existing.category}) deleted"
            )
        }
    }

    override suspend fun refreshExpenses() = cloudRefresh("ExpenseRepo") {
        val remoteExpenses = postgrest.from("expenses").select { filter { eq("is_deleted", false) } }.decodeList<ExpenseEntity>()
        transactionRunner.run {
            for (remote in remoteExpenses) {
                if (!syncQueueDao.isUnsynced("EXPENSE", remote.id)) {
                    expenseDao.insertExpense(remote.copy(isSynced = true))
                }
            }
        }
    }

    override suspend fun getFilteredExpensesPage(
        category: String? ,
        paymentMethod: String? ,
        fromDate: String? ,
        toDate: String? ,
        query: String? ,
        sortBy: String ,
        limit: Int ,
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
