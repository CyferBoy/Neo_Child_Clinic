package com.neochildclinic.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neochildclinic.data.local.entity.VaccinationItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaccinationItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<VaccinationItemEntity>)

    @Query("SELECT * FROM vaccination_items WHERE vaccinationId = :vaccinationId")
    fun getItemsForVaccination(vaccinationId: String): Flow<List<VaccinationItemEntity>>

    @Query("SELECT * FROM vaccination_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: String): VaccinationItemEntity?

    @Query("DELETE FROM vaccination_items WHERE vaccinationId = :vaccinationId")
    suspend fun deleteItemsForVaccination(vaccinationId: String)
}
