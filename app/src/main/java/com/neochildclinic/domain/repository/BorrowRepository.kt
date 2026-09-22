package com.neochildclinic.domain.repository

import com.neochildclinic.data.repository.BorrowRepositoryImpl

typealias BorrowRepository = BorrowRepositoryImpl

data class NewBatchInfo(
    val batchNumber: String,
    val expiryDate: String,
    val purchaseCost: Double = 0.0,
    val sellingPrice: Double = 0.0
)
