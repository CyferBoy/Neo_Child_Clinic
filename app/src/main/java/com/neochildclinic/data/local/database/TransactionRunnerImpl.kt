package com.neochildclinic.data.local.database

import com.neochildclinic.core.database.TransactionRunner
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRunnerImpl @Inject constructor(
    private val database: AppDatabase
) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T {
        return database.withTransaction<T> { block() }
    }
}