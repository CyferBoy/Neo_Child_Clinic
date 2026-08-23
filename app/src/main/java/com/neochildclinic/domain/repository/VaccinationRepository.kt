package com.neochildclinic.domain.repository

import com.neochildclinic.data.local.entity.PatientVaccinationCardEntity

import com.neochildclinic.domain.model.Vaccination
import kotlinx.coroutines.flow.Flow

interface VaccinationRepository {
    val allVaccinations: Flow<List<Vaccination>>
    fun getVaccinationsForPatient(patientId: String): Flow<List<Vaccination>>
    fun getVaccinationCardsForPatient(patientId: String): Flow<List<PatientVaccinationCardEntity>>
    suspend fun refreshVaccinations()
    // Split in two so the network fetch can run in parallel with other startup sync
    // tasks (no added latency), while the local insert - which needs vaccineId/batchId
    // to already exist locally - waits until inventory sync has actually completed.
    suspend fun fetchRemoteVaccinationItems(): List<com.neochildclinic.data.local.entity.VaccinationItemEntity>
    suspend fun applyDownloadedVaccinationItems(items: List<com.neochildclinic.data.local.entity.VaccinationItemEntity>)
    suspend fun getVaccinationById(id: String): Vaccination?
    suspend fun addVaccination(vaccination: Vaccination, transactionGroupId: String? = null)
    suspend fun deleteVaccination(id: String)
    suspend fun markAsDone(id: String)

    fun getTodayCount(date: String): Flow<Int>
    fun getTodayRevenue(date: String): Flow<Double?>
    fun getTodayCash(date: String): Flow<Double?>
    fun getTodayOnline(date: String): Flow<Double?>
    fun getMonthlyCount(pattern: String): Flow<Int>
    fun getMonthlyRevenue(pattern: String): Flow<Double?>
    fun getVaccineNamesForMonth(pattern: String): Flow<List<String>>
    suspend fun transferVaccinations(duplicateId: String, masterId: String)
}
