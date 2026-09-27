package com.neochildclinic.data

import com.neochildclinic.domain.TransactionRunner
import com.neochildclinic.data.local.database.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRunnerImpl @Inject constructor(
    private val database: AppDatabase
) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T {
        return database.withTransaction { block() }
    }
}