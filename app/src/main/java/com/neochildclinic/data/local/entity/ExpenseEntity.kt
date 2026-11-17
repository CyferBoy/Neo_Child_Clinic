package com.neochildclinic.data.local.entity
import com.neochildclinic.core.common.DateUtils

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.neochildclinic.domain.model.Expense
import com.neochildclinic.domain.model.ExpenseCategory
import com.neochildclinic.domain.model.ExpensePaymentMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Completely separate table from vaccinations/consultations/inventory/finance_transactions
 * (task section 2, 20). Follows the same id/timestamp/accountability/sync conventions
 * already used elsewhere in this project (see PatientEntity, WasteEntity,
 * VaccineBatchEntity) rather than inventing a new convention:
 *  - id: client-generated UUID primary key (PatientEntity, WasteEntity, etc.)
 *  - created_by/updated_by: @ColumnInfo(name = "created_by"/"updated_by") snake_case,
 *    matching migration22_23 and the migration17_18 pattern that added these columns to
 *    every other table.
 *  - createdAt/updatedAt: ISO-8601 strings via DateUtils.getCurrentIsoTimestamp(),
 *    matching AuditLogEntity/InventoryTransactionEntity.
 *  - isSynced/syncedAt: offline-first sync bookkeeping, matching every other entity.
 *
 * amountPaise is a Long (integer paise, 1 rupee = 100 paise) - never Double/Float - per
 * task section 2's explicit "do not use floating-point arithmetic for monetary
 * calculations" instruction. This intentionally differs from FinanceEntity.amount (Double),
 * which is pre-existing and left untouched.
 */
@Serializable
@Entity(
    tableName = "expenses",
    indices = [
        Index("expenseDate"),
        Index("category"),
        Index(name = "index_expenses_created_by", value = ["created_by"]),
        Index("updatedAt"),
        Index("createdAt")
    ]
)
data class ExpenseEntity(
    @PrimaryKey @SerialName("id") val id: String = java.util.UUID.randomUUID().toString(),
    @SerialName("expense_date") val expenseDate: String,
    @SerialName("category") val category: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("amount_paise") val amountPaise: Long,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("reference_number") val referenceNumber: String? = null,
    @SerialName("attachment_path") val attachmentPath: String? = null,
    @SerialName("created_at") val createdAt: String = com.neochildclinic.core.common.DateUtils.getCurrentIsoTimestamp(),
    @SerialName("updated_at") val updatedAt: String = com.neochildclinic.core.common.DateUtils.getCurrentIsoTimestamp(),
    @SerialName("created_by") @ColumnInfo(name = "created_by") val createdBy: String? = null,
    @SerialName("updated_by") @ColumnInfo(name = "updated_by") val updatedBy: String? = null,
    @SerialName("is_synced") override val isSynced: Boolean = false,
    @SerialName("synced_at") val syncedAt: String? = null
) : SyncableEntity

fun ExpenseEntity.toDomain() = Expense(
    id = id,
    expenseDate = expenseDate,
    category = ExpenseCategory.fromLabelOrName(category),
    title = title,
    description = description,
    amountPaise = amountPaise,
    paymentMethod = ExpensePaymentMethod.fromLabelOrName(paymentMethod),
    referenceNumber = referenceNumber,
    attachmentPath = attachmentPath,
    createdBy = createdBy,
    updatedBy = updatedBy,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isSynced = isSynced
)

fun Expense.toEntity(isSynced: Boolean = false) = ExpenseEntity(
    id = id,
    expenseDate = expenseDate,
    category = category.name,
    title = title,
    description = description,
    amountPaise = amountPaise,
    paymentMethod = paymentMethod.name,
    referenceNumber = referenceNumber,
    attachmentPath = attachmentPath,
    createdAt = createdAt ?: com.neochildclinic.core.common.DateUtils.getCurrentIsoTimestamp(),
    updatedAt = com.neochildclinic.core.common.DateUtils.getCurrentIsoTimestamp(),
    createdBy = createdBy,
    updatedBy = updatedBy,
    isSynced = isSynced,
    syncedAt = if (isSynced) com.neochildclinic.core.common.DateUtils.getCurrentIsoTimestamp() else null
)
