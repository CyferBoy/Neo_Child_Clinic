package com.neochildclinic.data.local.database

import androidx.room.Transactor
import androidx.room.useWriterConnection
import com.neochildclinic.core.database.TransactionRunner
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRunnerImpl @Inject constructor(
    private val database: AppDatabase
) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T {
        return database.useWriterConnection { transactor ->
            transactor.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
                block()
            }
        }
    }
}
