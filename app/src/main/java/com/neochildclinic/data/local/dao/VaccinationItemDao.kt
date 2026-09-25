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

    @Query("SELECT * FROM vaccination_items WHERE vaccinationId = :vaccinationId AND is_deleted = 0")
    fun getItemsForVaccination(vaccinationId: String): Flow<List<VaccinationItemEntity>>

    @Query("SELECT * FROM vaccination_items WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getItemById(id: String): VaccinationItemEntity?

    @Query("UPDATE vaccination_items SET is_deleted = 1, deleted_at = :deletedAt, deleted_by = :deletedBy WHERE vaccinationId = :vaccinationId AND is_deleted = 0")
    suspend fun deleteItemsForVaccination(vaccinationId: String, deletedAt: String, deletedBy: String?)

    @Query("UPDATE vaccination_items SET is_deleted = 1, deleted_at = :deletedAt, deleted_by = :deletedBy WHERE id IN (:ids) AND is_deleted = 0")
    suspend fun deleteItemsByIds(ids: List<String>, deletedAt: String, deletedBy: String?)
}
