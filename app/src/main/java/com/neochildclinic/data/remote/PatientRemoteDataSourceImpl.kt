package com.neochildclinic.data.remote

import com.neochildclinic.data.local.entity.PatientEntity
import com.neochildclinic.data.local.entity.InventoryTransactionEntity
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientRemoteDataSourceImpl @Inject constructor(
    private val postgrest: Postgrest
) : PatientRemoteDataSource {

    override suspend fun fetchAllPatients(): List<PatientEntity> {
        return postgrest.from("patients")
            .select { filter { eq("is_deleted", false) } }
            .decodeList<PatientEntity>()
    }

    override suspend fun fetchPatientById(id: String): PatientEntity? {
        return try {
            postgrest.from("patients")
                .select { filter { eq("id", id); eq("is_deleted", false) } }
                .decodeSingleOrNull<PatientEntity>()
        } catch (e: Exception) {
            android.util.Log.e("PatientRemoteDS", "Failed to fetch patient $id", e)
            null
        }
    }

    override suspend fun getStockHistoryPage(
        vaccineId: String?,
        batchId: String?,
        types: List<String>,
        typesEmpty: Boolean,
        fromDateIso: String?,
        toDateIso: String?,
        limit: Int,
        offset: Int
    ): List<InventoryTransactionEntity> {
        return withContext(Dispatchers.IO) {
            postgrest.from("inventory_transactions").select {
                filter {
                    if (vaccineId != null) eq("vaccine_id", vaccineId)
                    if (batchId != null) eq("batch_id", batchId)
                    if (types.isNotEmpty()) isIn("transaction_type", types)
                    if (fromDateIso != null) gte("timestamp", fromDateIso)
                    if (toDateIso != null) lte("timestamp", toDateIso + "T23:59:59")
                }
                order("timestamp", Order.DESCENDING)
                range(offset.toLong(), (offset + limit - 1).toLong())
            }.decodeList<InventoryTransactionEntity>()
        }
    }
}
