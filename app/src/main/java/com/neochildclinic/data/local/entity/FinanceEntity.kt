package com.neochildclinic.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.neochildclinic.domain.model.FinanceTransaction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "finance_transactions",
    indices = [Index("patientId"), Index("visitId"), Index("timestamp"), Index("transaction_date")]
)
data class FinanceEntity(
    @PrimaryKey @SerialName("id") val id: String = java.util.UUID.randomUUID().toString(),
    @SerialName("timestamp") val timestamp: String = com.neochildclinic.core.common.DateUtils.getCurrentIsoTimestamp(),
    @SerialName("transaction_date") @ColumnInfo(name = "transaction_date") val transactionDate: String? = null,
    @SerialName("type") val type: String, // INCOME, EXPENSE
    @SerialName("category") val category: String, // VACCINATION, CONSULTATION, PURCHASE, etc.
    @SerialName("amount") val amount: Double,
    @SerialName("cash_amount") val cashAmount: Double = 0.0,
    @SerialName("online_amount") val onlineAmount: Double = 0.0,
    @SerialName("currency") val currency: String = "INR",
    @SerialName("payment_method") val paymentMethod: String, // CASH, ONLINE, MIXED
    @SerialName("patient_id") val patientId: String? = null,
    @SerialName("visit_id") val visitId: String? = null,
    @SerialName("reference_number") val referenceNumber: String? = null, // Receipt number
    @SerialName("remarks") val remarks: String? = null,
    @SerialName("recorded_by") val recordedBy: String,
    @SerialName("is_synced") override val isSynced: Boolean = false,
    @SerialName("created_by") @ColumnInfo(name = "created_by") val createdBy: String? = null,
    @SerialName("updated_by") @ColumnInfo(name = "updated_by") val updatedBy: String? = null
) : SyncableEntity

fun FinanceEntity.toDomain() = FinanceTransaction(
    id = id,
    timestamp = timestamp,
    transactionDate = transactionDate,
    type = type,
    category = category,
    amount = amount,
    cashAmount = cashAmount,
    onlineAmount = onlineAmount,
    paymentMethod = paymentMethod,
    patientId = patientId,
    visitId = visitId,
    remarks = remarks
)
