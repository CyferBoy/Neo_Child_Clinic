package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.WasteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WasteDao {
    @Query("SELECT * FROM waste_records ORDER BY dateWasted DESC")
    fun getAllWaste(): Flow<List<WasteEntity>>

    @Query("SELECT * FROM waste_records WHERE id = :id LIMIT 1")
    suspend fun getWasteById(id: String): WasteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaste(waste: WasteEntity)


    @Query("DELETE FROM waste_records WHERE id = :id")
    suspend fun deleteWaste(id: String)


    @Query("SELECT * FROM waste_records WHERE vaccineId = :vaccineId ORDER BY dateWasted DESC")
    fun getWasteForVaccine(vaccineId: String): Flow<List<WasteEntity>>

    @Query("SELECT COUNT(*) FROM waste_records")
    fun getWasteCount(): Flow<Int>

    // Remote-deletion propagation (RemotePrune wiring, Phase 2 Batch 1 / NCC-023).
    @Query("SELECT id FROM waste_records")
    suspend fun getWasteIds(): List<String>

    @Query("SELECT id FROM waste_records WHERE isSynced = 1")
    suspend fun getSyncedWasteIds(): List<String>

}
