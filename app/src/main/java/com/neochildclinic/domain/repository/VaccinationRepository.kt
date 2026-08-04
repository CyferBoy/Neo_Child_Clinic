package com.neochildclinic.domain.repository

import com.neochildclinic.domain.model.Vaccination
import kotlinx.coroutines.flow.Flow

interface VaccinationRepository {
    val allVaccinations: Flow<List<Vaccination>>
    fun getVaccinationsForPatient(patientId: String): Flow<List<Vaccination>>
    suspend fun refreshVaccinations()
    suspend fun addVaccination(vaccination: Vaccination)
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
