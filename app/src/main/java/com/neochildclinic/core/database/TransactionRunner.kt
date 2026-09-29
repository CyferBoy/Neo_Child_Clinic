package com.neochildclinic.core.database

interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}