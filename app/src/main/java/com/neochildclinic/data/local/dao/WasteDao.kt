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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWasteRecords(waste: List<WasteEntity>)

    @Query("DELETE FROM waste_records WHERE id = :id")
    suspend fun deleteWaste(id: String)

    @Query("SELECT * FROM waste_records WHERE isSynced = 0")
    suspend fun getUnsyncedWaste(): List<WasteEntity>

    @Query("UPDATE waste_records SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("SELECT * FROM waste_records WHERE vaccineId = :vaccineId ORDER BY dateWasted DESC")
    fun getWasteForVaccine(vaccineId: String): Flow<List<WasteEntity>>

    @Query("SELECT COUNT(*) FROM waste_records")
    fun getWasteCount(): Flow<Int>
}
