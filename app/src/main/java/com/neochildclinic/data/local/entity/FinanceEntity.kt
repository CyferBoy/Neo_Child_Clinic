package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "finance_transactions",
    indices = [Index("patientId"), Index("visitId"), Index("timestamp")]
)
data class FinanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // INCOME, EXPENSE, REFUND
    val category: String, // VACCINATION, CONSULTATION, PURCHASE, etc.
    val amount: Double,
    val currency: String = "INR",
    val paymentMethod: String, // CASH, ONLINE, MIXED
    val patientId: String? = null,
    val visitId: String? = null,
    val referenceNumber: String? = null, // Receipt number
    val remarks: String? = null,
    val recordedBy: String,
    val isSynced: Boolean = false
)
