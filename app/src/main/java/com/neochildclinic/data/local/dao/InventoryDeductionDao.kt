package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.InventoryDeductionEntity

@Dao
interface InventoryDeductionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: InventoryDeductionEntity)

    @Query("SELECT * FROM inventory_deductions WHERE vaccinationId = :vaccinationId")
    suspend fun getForVaccination(vaccinationId: String): List<InventoryDeductionEntity>

    @Query("SELECT * FROM inventory_deductions WHERE vaccinationId = :vaccinationId AND status = 'COMPLETED'")
    suspend fun getCompletedForVaccination(vaccinationId: String): List<InventoryDeductionEntity>

    @Query("DELETE FROM inventory_deductions WHERE vaccinationId = :vaccinationId")
    suspend fun deleteForVaccination(vaccinationId: String)
}
