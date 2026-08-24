package com.neochildclinic.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "finance_transactions",
    indices = [Index("patientId"), Index("visitId"), Index("timestamp")]
)
data class FinanceEntity(
    @PrimaryKey @SerialName("id") val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: String = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
    val type: String, // INCOME, EXPENSE
    val category: String, // VACCINATION, CONSULTATION, PURCHASE, etc.
    val amount: Double,
    @SerialName("cash_amount") val cashAmount: Double = 0.0,
    @SerialName("online_amount") val onlineAmount: Double = 0.0,
    val currency: String = "INR",
    @SerialName("payment_method") val paymentMethod: String, // CASH, ONLINE, MIXED
    @SerialName("patient_id") val patientId: String? = null,
    @SerialName("visit_id") val visitId: String? = null,
    @SerialName("reference_number") val referenceNumber: String? = null, // Receipt number
    val remarks: String? = null,
    @SerialName("recorded_by") val recordedBy: String,
    @SerialName("is_synced") val isSynced: Boolean = false,
    @SerialName("created_by") @ColumnInfo(name = "created_by") val createdBy: String? = null,
    @SerialName("updated_by") @ColumnInfo(name = "updated_by") val updatedBy: String? = null
)
