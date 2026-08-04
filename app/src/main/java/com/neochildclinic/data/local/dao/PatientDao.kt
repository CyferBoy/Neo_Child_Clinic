package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.PatientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    @Query("SELECT * FROM patients WHERE isDeleted = 0")
    fun getAllPatients(): Flow<List<PatientEntity>>

    @Query("SELECT * FROM patients WHERE id = :id")
    suspend fun getPatientById(id: String): PatientEntity?

    @Query("SELECT * FROM patients WHERE patientClinicId = :clinicId AND isDeleted = 0 LIMIT 1")
    suspend fun getPatientByClinicId(clinicId: String): PatientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatient(patient: PatientEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatients(patients: List<PatientEntity>)

    @Query("UPDATE patients SET isDeleted = 1, isSynced = 0 WHERE id = :id")
    suspend fun deletePatient(id: String)

    @Query("SELECT * FROM patients WHERE isSynced = 0")
    suspend fun getUnsyncedPatients(): List<PatientEntity>

    @Query("UPDATE patients SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("""
        SELECT * FROM patients 
        WHERE isDeleted = 0 
        AND (name LIKE :q OR phone LIKE :q OR parentName LIKE :q OR patientClinicId LIKE :q OR village LIKE :q OR address LIKE :q
             OR id IN (SELECT patientId FROM patient_visits WHERE isDeleted = 0 AND (vaccineNames LIKE :q OR receiptNumber LIKE :q)))
    """)
    fun searchPatients(q: String): Flow<List<PatientEntity>>

    @Query("SELECT COUNT(*) FROM patients WHERE isDeleted = 0")
    fun getPatientCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM patients")
    suspend fun getTotalPatientCount(): Int

    @Query("SELECT * FROM patients WHERE (patientClinicId IS NULL OR TRIM(patientClinicId) = '' OR patientClinicId LIKE 'TEMP-%' OR patientClinicId LIKE '%-DUP-%') AND isDeleted = 0")
    suspend fun getPatientsNeedingId(): List<PatientEntity>

    @Query("SELECT patientClinicId FROM patients WHERE patientClinicId LIKE 'NEO-%' AND patientClinicId NOT LIKE '%-DUP-%' AND patientClinicId NOT LIKE '%-CONFLICT-%' ORDER BY CAST(SUBSTR(patientClinicId, 5) AS INTEGER) DESC LIMIT 1")
    suspend fun getMaxClinicId(): String?
}
