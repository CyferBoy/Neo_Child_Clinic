package com.neochildclinic.data.remote

import com.neochildclinic.core.sync.fetchAllPages
import com.neochildclinic.data.local.entity.PatientEntity
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientRemoteDataSourceImpl @Inject constructor(
    private val postgrest: Postgrest
) : PatientRemoteDataSource {

    override suspend fun fetchAllPatients(): List<PatientEntity> {
        return fetchAllPages { from, to ->
            postgrest.from("patients")
                .select {
                    filter { eq("is_deleted", false) }
                    order("id", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<PatientEntity>()
        }
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
}
