package com.neochildclinic.domain.repository

interface WasteRepository {
    suspend fun refreshWaste()
}