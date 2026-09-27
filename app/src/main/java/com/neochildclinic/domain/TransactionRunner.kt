package com.neochildclinic.domain

interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}