package com.neochildclinic.domain.model

data class FinanceTransaction(
    val id: String,
    val timestamp: String,
    val transactionDate: String? = null,
    val type: String,
    val category: String,
    val amount: Double,
    val cashAmount: Double = 0.0,
    val onlineAmount: Double = 0.0,
    val paymentMethod: String,
    val patientId: String? = null,
    val visitId: String? = null,
    val remarks: String? = null
)