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

    // Pull loops read the whole table once and build both lookup maps from it (see
    // PatientRepositoryImpl.refreshPatients). No bind parameters, so neither the empty-list
    // edge case nor SQLite's 999-variable ceiling (API 29/30) applies.
    @Query("SELECT * FROM patients")
    suspend fun getAllPatientsSnapshot(): List<PatientEntity>

    @Query("SELECT * FROM patients WHERE patientClinicId = :clinicId LIMIT 1")
    suspend fun getPatientByClinicId(clinicId: String): PatientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatient(patient: PatientEntity)

    // Create path: a unique-clinicId conflict must ABORT instead of REPLACE, which
    // would DELETE the other patient's row and orphan every child record (visits,
    // consultations, reminders, notes) pointing at it.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPatientStrict(patient: PatientEntity)

    // Pull/update path. @Upsert never deletes the row it writes, so the CASCADEs onto
    // patient_visits/consultations/reminders/patient_notes/personal_vaccine_reminders do not
    // fire. insertPatient() above is REPLACE (= DELETE + INSERT) and does delete them - it is
    // kept only for rows with no local children. See PullUpsertPreservesChildrenTest.
    @Upsert
    suspend fun upsertPatient(patient: PatientEntity)


    @Query("DELETE FROM patients WHERE id = :id")
    suspend fun deletePatient(id: String)

    @Query("""
        SELECT * FROM patients 
        WHERE (name LIKE :q OR phone LIKE :q OR patientClinicId LIKE :q OR address LIKE :q
             OR id IN (SELECT patientId FROM patient_visits WHERE vaccineNames LIKE :q OR receiptNumber LIKE :q))
    """)
    fun searchPatients(q: String): Flow<List<PatientEntity>>

    @Query("SELECT COUNT(*) FROM patients")
    fun getPatientCount(): Flow<Int>

    // Patients that have at least one visit with totalPaid <= 0.0, i.e. the "missing price"
    // badge on the patient list. Reading just the one column of patient_visits avoids
    // loading the whole table plus every vaccination_items row behind it.
    // Semantics deliberately preserved: any (not all) visit at <= 0.0 flags the patient, and a
    // patient with no visits at all is never flagged. No is_deleted predicate - migration
    // 31->32 dropped that column from patient_visits; local deletes are hard deletes.
    @Query("SELECT DISTINCT patientId FROM patient_visits WHERE totalPaid <= 0.0")
    fun getPatientIdsWithMissingPrice(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM patients")
    suspend fun getTotalPatientCount(): Int

    @Query("SELECT * FROM patients WHERE (patientClinicId IS NULL OR TRIM(patientClinicId) = '' OR patientClinicId LIKE 'TEMP-%' OR patientClinicId LIKE '%-DUP-%')")
    suspend fun getPatientsNeedingId(): List<PatientEntity>

    @Query("SELECT patientClinicId FROM patients WHERE patientClinicId LIKE 'NEO-%' AND patientClinicId NOT LIKE '%-DUP-%' AND patientClinicId NOT LIKE '%-CONFLICT-%' ORDER BY CAST(SUBSTR(patientClinicId, 5) AS INTEGER) DESC LIMIT 1")
    suspend fun getMaxClinicId(): String?

    // Widget-facing projection (WidgetWorker): id + name only, for the handful of due-list
    // patient ids. The worker used to evaluate the entire allPatients Flow and build a map
    // from the full table on every widget refresh; two handfuls of rows beat that.
    data class PatientNameRef(val id: String, val name: String)

    @Query("SELECT id, name FROM patients WHERE id IN (:ids)")
    suspend fun getNamesByIds(ids: List<String>): List<PatientNameRef>

    // Remote-deletion propagation (RemotePrune wiring, Phase 2 Batch 1 / NCC-023):
    // synced ids are prune-eligible when the pull no longer returns them.
    @Query("SELECT id FROM patients")
    suspend fun getPatientIds(): List<String>

    @Query("SELECT id FROM patients WHERE isSynced = 1")
    suspend fun getSyncedPatientIds(): List<String>

}
