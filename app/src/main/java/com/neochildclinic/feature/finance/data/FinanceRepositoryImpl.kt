package com.neochildclinic.feature.finance.data
import com.neochildclinic.core.database.TransactionRunner
import com.neochildclinic.data.local.dao.VaccinationDao
import com.neochildclinic.feature.finance.domain.repository.FinanceRepository
import com.neochildclinic.feature.sync.domain.repository.SyncRepository

import com.neochildclinic.data.local.dao.FinanceDao
import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.FinanceTransaction
import com.neochildclinic.feature.statistics.domain.FinanceCalculator
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.core.logger.AuditLogger
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.neochildclinic.core.sync.cloudRefresh
import com.neochildclinic.core.sync.fetchAllPages
import com.neochildclinic.core.sync.RemotePrune

@Singleton
class FinanceRepositoryImpl @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val financeDao: FinanceDao,
    private val vaccinationDao: VaccinationDao,
    private val postgrest: Postgrest,
    private val syncRepository: SyncRepository,
    private val syncQueueDao: com.neochildclinic.data.local.dao.SyncQueueDao,
    private val auditLogger: AuditLogger,
    private val sessionManager: com.neochildclinic.core.security.SessionManager
) : FinanceRepository {

    /**
     * Deterministic row id for a visit's VACCINATION income transaction. Each visit is only
     * ever supposed to carry one payment entry (see recordVaccination()/updateIncomeForVisit()
     * comments), but that invariant used to depend entirely on the *local* Room cache already
     * containing the row before an edit runs: if a device edits a visit's income before it has
     * synced down the row another device created (or right after a fresh install/reinstall,
     * before the first sync completes), updateIncomeForVisit()'s "existing == null" fallback
     * would insert a brand-new row with a random id instead of updating the real one. Both
     * Room (OnConflictStrategy.REPLACE) and the Postgres sync (upsert) key off `id`, so once
     * every vaccination-income row for a given visit uses this same deterministic id, any
     * device that thinks it's "creating" one actually just overwrites the same row - no more
     * silent duplicate rows that later got invisibly discarded when finance stats deduplicate
     * by visit and keep only the newest, dropping the other row's amount from every total.
     */
    private fun vaccinationIncomeRowId(visitId: String): String =
        java.util.UUID.nameUUIDFromBytes("visit_income:$visitId".toByteArray(Charsets.UTF_8)).toString()

    private suspend fun resolveTransactionDate(visitId: String?): String? {
        if (visitId.isNullOrBlank()) return null
        val dateGiven = vaccinationDao.getVaccinationById(visitId)?.dateGiven
        if (dateGiven.isNullOrBlank()) return null
        
        val parsed = com.neochildclinic.core.common.PatientUtils.parseDate(dateGiven) ?: return null
        return parsed.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    }

    override fun getAllTransactions(): Flow<List<FinanceTransaction>> =
        financeDao.getAllTransactions().map { rows -> rows.map { it.toDomain() } }

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
        transactionRunner.run {
            val userName = sessionManager.getCurrentUserName()
            // A visit is only supposed to ever have one VACCINATION income row (see
            // vaccinationIncomeRowId doc above) - use the deterministic id here too so this
            // "create" path can never diverge from what updateIncomeForVisit() writes.
            val deterministicId = if (category.equals("VACCINATION", true) && !visitId.isNullOrBlank()) {
                vaccinationIncomeRowId(visitId)
            } else null
            val transaction = FinanceEntity(
                id = deterministicId ?: java.util.UUID.randomUUID().toString(),
                type = "INCOME",
                transactionDate = resolveTransactionDate(visitId),
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
                remarks = remarks ?: "Consultation",
                recordedBy = userName,
                isSynced = false,
                createdBy = userName,
                updatedBy = userName
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

    override suspend fun updateConsultationIncome(
        visitId: String,
        consultationId: String,
        originalAmount: Double,
        originalCashAmount: Double,
        originalOnlineAmount: Double,
        amount: Double,
        cashAmount: Double,
        onlineAmount: Double,
        remarks: String?,
        recordedBy: String,
        transactionGroupId: String?
    ) {
        transactionRunner.run {
            val consultationTransactions = financeDao.getTransactionsByVisitId(visitId)
                .filter { it.type == "INCOME" && it.category == "CONSULTATION" }

            // Identify the transaction belonging to the consultation being edited.
            // Prefer an exact match to the persisted payment snapshot instead of
            // blindly updating the latest consultation transaction for the visit.
            // Prefer the stable consultation marker so another consultation's
            // income cannot be modified when a visit has multiple finance rows.
            val marker = "[CONSULTATION_ID:$consultationId]"
            val markedMatches = consultationTransactions.filter {
                it.remarks?.contains(marker) == true
            }

            // Compare money using cents rather than exact Double equality.
            // This keeps legacy fallback matching stable when values such as 500.00
            // are represented with tiny floating-point differences.
            fun moneyCents(value: Double): Long = kotlin.math.round(value * 100.0).toLong()

            val originalAmountCents = moneyCents(originalAmount)
            val originalCashCents = moneyCents(originalCashAmount)
            val originalOnlineCents = moneyCents(originalOnlineAmount)

            val exactMatches = consultationTransactions.filter {
                moneyCents(it.amount) == originalAmountCents &&
                    moneyCents(it.cashAmount) == originalCashCents &&
                    moneyCents(it.onlineAmount) == originalOnlineCents
            }

            val existing = when {
                markedMatches.size == 1 -> markedMatches.first()
                markedMatches.size > 1 -> throw IllegalStateException(
                    "Multiple finance transactions are linked to consultation $consultationId"
                )
                exactMatches.size == 1 -> exactMatches.first()
                consultationTransactions.size == 1 -> consultationTransactions.first()
                exactMatches.isEmpty() -> throw IllegalStateException(
                    "Unable to identify the consultation finance transaction for consultation $consultationId"
                )
                else -> throw IllegalStateException(
                    "Multiple possible finance transactions for consultation $consultationId"
                )
            }

            val paymentMethod = when {
                cashAmount > 0 && onlineAmount > 0 -> "MIXED"
                cashAmount > 0 -> "CASH"
                onlineAmount > 0 -> "ONLINE"
                else -> "FREE"
            }

            // Always preserve the consultation marker. Without this, the first edit
            // would remove the marker and later edits would have to fall back to
            // payment-value matching, which can be ambiguous when a visit has
            // multiple consultation transactions.
            val baseRemarks = remarks?.trim().orEmpty()
            val preservedRemarks = if (baseRemarks.contains(marker)) {
                baseRemarks
            } else if (baseRemarks.isEmpty()) {
                marker
            } else {
                "$baseRemarks $marker"
            }

            val userName = sessionManager.getCurrentUserName()
            val updated = existing.copy(
                transactionDate = resolveTransactionDate(visitId),
                amount = amount,
                cashAmount = cashAmount,
                onlineAmount = onlineAmount,
                paymentMethod = paymentMethod,
                remarks = preservedRemarks,
                recordedBy = userName,
                isSynced = false,
                createdBy = existing.createdBy ?: userName,
                updatedBy = userName
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
                oldValue = existing.amount.toString(),
                newValue = amount.toString(),
                remarks = "Consultation income updated for visit $visitId",
                transactionGroupId = transactionGroupId
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
        transactionRunner.run {
            val transactions = financeDao.getTransactionsByVisitId(visitId)
                .filter { it.type == "INCOME" && it.category == "VACCINATION" }

            // Keep a vaccination finance record even when the fee is zero so historical
            // vaccine COGS remains reportable. Never delete historical finance rows here.

            val existing = transactions.maxByOrNull { it.timestamp }
            val userName = sessionManager.getCurrentUserName()
            if (existing == null) {
                // Local Room may simply not have the row yet (another device created it and
                // sync hasn't pulled it down, or the app was just reinstalled) - use the same
                // deterministic id recordIncome() would have used, so this "create" can only
                // ever overwrite that one row (via Room REPLACE / Postgres upsert) instead of
                // inserting a second, genuinely duplicate row that later silently drops one
                // visit's payment out of every total.
                val transaction = FinanceEntity(
                    id = vaccinationIncomeRowId(visitId),
                    type = "INCOME",
                    transactionDate = resolveTransactionDate(visitId),
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
                    patientId = vaccinationDao.getVaccinationById(visitId)?.patientId,
                    visitId = visitId,
                    remarks = remarks,
                    recordedBy = userName,
                    isSynced = false,
                    createdBy = userName,
                    updatedBy = userName
                )
                financeDao.insertTransaction(transaction)
                syncRepository.enqueue(entityName = "FINANCE", entityId = transaction.id, operation = SyncOperation.CREATE, priority = SyncPriority.MEDIUM, transactionGroupId = transactionGroupId)
                return@run
            }

            val existingTransaction = existing

            val paymentMethod = when {
                cashAmount > 0 && onlineAmount > 0 -> "MIXED"
                cashAmount > 0 -> "CASH"
                onlineAmount > 0 -> "ONLINE"
                else -> "FREE"
            }

            val snapshot = existingTransaction.remarks?.substringAfter("[COGS_SNAPSHOT:", missingDelimiterValue = "")?.substringBefore("]")?.toDoubleOrNull()
            // The edit path (VaccinationEditEngine/ClinicalVaccinationService) rebuilds
            // remarks with the current items' COGS; keep the stored snapshot only when the
            // caller didn't supply one, otherwise edits to items/prices never update profit.
            val incomingHasSnapshot = remarks?.contains("[COGS_SNAPSHOT:") == true
            val updatedRemarks = if (snapshot != null && !incomingHasSnapshot) existingTransaction.remarks else remarks
            val updated = existingTransaction.copy(
                transactionDate = resolveTransactionDate(visitId),
                amount = amount,
                cashAmount = cashAmount,
                onlineAmount = onlineAmount,
                paymentMethod = paymentMethod,
                patientId = existing.patientId,
                visitId = visitId,
                remarks = updatedRemarks,
                recordedBy = userName,
                isSynced = false,
                createdBy = existing.createdBy ?: userName,
                updatedBy = userName
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
        transactionRunner.run {
            val validById = vaccinations.associateBy { it.id }
            val transactions = financeDao.getAllTransactionsSnapshot()
            transactions
                .filter { it.type.equals("INCOME", true) && it.category.equals("VACCINATION", true) }
                .forEach { transaction ->
                    val visitId = transaction.visitId ?: return@forEach
                    val existingCogs = FinanceCalculator.cogsOf(transaction.remarks)
                    val vaccination = validById[visitId] ?: return@forEach
                    val cogs = vaccination.items.sumOf { item ->
                        item.netRate.coerceAtLeast(0.0) * item.quantity.coerceAtLeast(0)
                    }
                    // A marked non-zero snapshot is historical: do not overwrite it. A marked
                    // zero snapshot may have been written before the items landed (the old
                    // ordering bug), so repair it once the items produce a non-zero cost.
                    if (existingCogs != null && (existingCogs != 0.0 || cogs == 0.0)) return@forEach
                    val snapshotRemarks = FinanceCalculator.buildVaccinationRemarks(
                        vaccination.items.joinToString(", ") { it.vaccineName },
                        cogs
                    )
                    // Replace any old marker instead of appending a second one.
                    val baseRemarks = transaction.remarks
                        ?.substringBefore("[COGS_SNAPSHOT:")
                        ?.trimEnd()
                        ?.takeIf { it.isNotEmpty() }
                    val updatedRemarks = if (baseRemarks == null) {
                        snapshotRemarks
                    } else {
                        "$baseRemarks $snapshotRemarks"
                    }
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

    override suspend fun refreshTransactions() = cloudRefresh("FinanceRepo", rethrow = true) {
                // is_deleted filter matches the convention used by the 13 other refresh paths:
                // rows soft-deleted by an earlier build are still on the server and would
                // otherwise be re-imported here and counted in revenue/profit totals.
                val transactions = fetchAllPages { from, to ->
                    postgrest.from("finance_transactions")
                        .select {
                            filter { eq("is_deleted", false) }
                            order("id", Order.ASCENDING)
                            range(from, to)
                        }
                        .decodeList<FinanceEntity>()
                }
                transactionRunner.run {
                    val pendingFinanceIds = syncQueueDao.unsyncedIdSet("FINANCE")
                    val visitDao = vaccinationDao
                    for (remote in transactions) {
                        var normalizedRemote = remote
                        
                        // Self-healing: Ensure transaction date matches the visit date if linked
                        val visitId = remote.visitId
                        var wasHealed = false
                        if (!visitId.isNullOrBlank()) {
                            val visit = visitDao.getVaccinationById(visitId)
                            if (visit != null) {
                                val visitDate = com.neochildclinic.core.common.PatientUtils.parseDate(visit.dateGiven)
                                    ?.toInstant()?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate()?.toString()
                                if (visitDate != null && remote.transactionDate != visitDate) {
                                    normalizedRemote = remote.copy(transactionDate = visitDate)
                                    wasHealed = true
                                }
                            }
                        }

                        val local = financeDao.getTransactionById(normalizedRemote.id)
                        // Also honour the sync queue, like the other 13 pull paths: a row
                        // edited while its upload was in flight is still isSynced=true
                        // (markUploaded clears the flag by id only), so trusting that flag
                        // alone let a concurrent pull overwrite the newer local edit - and the
                        // rolled-back version was then uploaded, losing the edit on every device.
                        // The pending set gates BOTH branches: without it, `local == null`
                        // re-imported a row we hard-deleted while its DELETE upload was still
                        // in flight, and it resurfaced in Statistics (NCC-044).
                        var shouldUpdateLocal = financeImportAllowed(
                            local = local,
                            remoteId = normalizedRemote.id,
                            pendingIds = pendingFinanceIds
                        )
                        var finalEntity = normalizedRemote.copy(isSynced = true)

                        if (local != null && wasHealed) {
                            finalEntity = normalizedRemote.copy(isSynced = false)
                            shouldUpdateLocal = true
                        }

                        if (shouldUpdateLocal) {
                            financeDao.insertTransaction(finalEntity)
                            if (wasHealed && !finalEntity.isSynced) {
                                syncRepository.enqueue(
                                    entityName = "FINANCE",
                                    entityId = finalEntity.id,
                                    operation = SyncOperation.UPDATE,
                                    priority = SyncPriority.MEDIUM
                                )
                            }
                        }
                    }
                    // Remote-deletion propagation (NCC-023 / DA-01): synced transactions the
                    // remote set no longer lists and no queue entry claims are drift.
                    RemotePrune.computeBatches(
                        localIds = financeDao.getFinanceIds(),
                        syncedIds = financeDao.getSyncedFinanceIds().toSet(),
                        remoteIds = transactions.mapTo(HashSet()) { it.id },
                        pendingIds = pendingFinanceIds
                    ).flatten().forEach { financeDao.deleteTransactionById(it) }
                }
    }
}

/**
 * Whether a pulled remote finance row may be written into Room. Same contract as every other
 * refresh path (see todoUpsertsForRefresh): the pending queue entry is authoritative. The
 * pending set must gate the `local == null` case too - a locally hard-deleted row whose remote
 * DELETE upload was still in flight used to pass that case, get re-imported as isSynced=true,
 * resurface in Statistics > Finance / Monthly Finance Details, and stay un-prunable while the
 * DELETE remained pending (NCC-044).
 */
internal fun financeImportAllowed(
    local: FinanceEntity?,
    remoteId: String,
    pendingIds: Set<String>
): Boolean = remoteId !in pendingIds && (local == null || local.isSynced)

