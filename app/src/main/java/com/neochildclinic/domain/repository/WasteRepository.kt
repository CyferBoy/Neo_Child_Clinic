package com.neochildclinic.domain.repository

import com.neochildclinic.domain.model.WasteRecord
import kotlinx.coroutines.flow.Flow

interface WasteRepository {
    fun getAllWaste(): Flow<List<WasteRecord>>
    suspend fun getWasteById(id: String): WasteRecord?
    suspend fun recordWaste(record: WasteRecord, user: String)
    suspend fun updateWaste(oldRecord: WasteRecord, newRecord: WasteRecord, user: String)
    suspend fun deleteWaste(id: String, user: String)
    suspend fun refreshWaste()
    fun getWasteCount(): Flow<Int>
}
