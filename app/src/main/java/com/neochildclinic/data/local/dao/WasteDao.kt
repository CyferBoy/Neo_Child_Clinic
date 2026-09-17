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


    @Query("UPDATE waste_records SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("SELECT * FROM waste_records WHERE vaccineId = :vaccineId ORDER BY dateWasted DESC")
    fun getWasteForVaccine(vaccineId: String): Flow<List<WasteEntity>>

    @Query("SELECT COUNT(*) FROM waste_records")
    fun getWasteCount(): Flow<Int>

    // --- Pagination (large-data scalability pass) --- additive, existing Flow methods
    // above are untouched. dateWasted and vaccineId are both indexed (see WasteEntity).
    @Query("SELECT * FROM waste_records ORDER BY dateWasted DESC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun getAllWastePage(limit: Int, offset: Int): List<WasteEntity>

    @Query("SELECT * FROM waste_records WHERE vaccineId = :vaccineId ORDER BY dateWasted DESC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun getWasteForVaccinePage(vaccineId: String, limit: Int, offset: Int): List<WasteEntity>
}
