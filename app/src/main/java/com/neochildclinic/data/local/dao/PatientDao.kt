package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.PatientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    @Query("SELECT * FROM patients")
    fun getAllPatients(): Flow<List<PatientEntity>>

    @Query("SELECT * FROM patients WHERE id = :id")
    suspend fun getPatientById(id: String): PatientEntity?

    @Query("SELECT * FROM patients WHERE patientClinicId = :clinicId LIMIT 1")
    suspend fun getPatientByClinicId(clinicId: String): PatientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatient(patient: PatientEntity)


    @Query("DELETE FROM patients WHERE id = :id")
    suspend fun deletePatient(id: String)


    @Query("UPDATE patients SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("""
        SELECT * FROM patients 
        WHERE (name LIKE :q OR phone LIKE :q OR patientClinicId LIKE :q OR address LIKE :q
             OR id IN (SELECT patientId FROM patient_visits WHERE vaccineNames LIKE :q OR receiptNumber LIKE :q))
    """)
    fun searchPatients(q: String): Flow<List<PatientEntity>>

    // --- Pagination (large-data scalability pass) ---
    // getAllPatients()/searchPatients() above are unbounded and were the biggest risk in
    // the app for large clinics - every screen backed by them loads the entire patients
    // table into memory. These paginated variants are additive: existing call sites are
    // untouched, callers that need bounded pages (e.g. an infinite-scroll patient list)
    // can migrate to these. name has an index (see PatientEntity), so ORDER BY name is
    // indexed; id is included as a tiebreak for patients sharing a name so paging is stable.

    @Query("SELECT * FROM patients ORDER BY name COLLATE NOCASE ASC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun getPatientsPage(limit: Int, offset: Int): List<PatientEntity>

    // Keyset/cursor variant: avoids the cost of a large OFFSET (SQLite still has to walk
    // and discard `offset` rows before it can return anything). Pass the name/id of the
    // last row from the previous page (empty strings for the first page).
    @Query(
        """
        SELECT * FROM patients
        WHERE (name COLLATE NOCASE > :lastName) OR (name COLLATE NOCASE = :lastName AND id > :lastId)
        ORDER BY name COLLATE NOCASE ASC, id ASC
        LIMIT :limit
        """
    )
    suspend fun getPatientsAfter(lastName: String, lastId: String, limit: Int): List<PatientEntity>

    @Query(
        """
        SELECT * FROM patients 
        WHERE (name LIKE :q OR phone LIKE :q OR patientClinicId LIKE :q OR address LIKE :q
             OR id IN (SELECT patientId FROM patient_visits WHERE vaccineNames LIKE :q OR receiptNumber LIKE :q))
        ORDER BY name COLLATE NOCASE ASC, id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun searchPatientsPage(q: String, limit: Int, offset: Int): List<PatientEntity>

    @Query("SELECT COUNT(*) FROM patients")
    fun getPatientCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM patients")
    suspend fun getTotalPatientCount(): Int

    @Query("SELECT * FROM patients WHERE (patientClinicId IS NULL OR TRIM(patientClinicId) = '' OR patientClinicId LIKE 'TEMP-%' OR patientClinicId LIKE '%-DUP-%')")
    suspend fun getPatientsNeedingId(): List<PatientEntity>

    @Query("SELECT patientClinicId FROM patients WHERE patientClinicId LIKE 'NEO-%' AND patientClinicId NOT LIKE '%-DUP-%' AND patientClinicId NOT LIKE '%-CONFLICT-%' ORDER BY CAST(SUBSTR(patientClinicId, 5) AS INTEGER) DESC LIMIT 1")
    suspend fun getMaxClinicId(): String?
}
