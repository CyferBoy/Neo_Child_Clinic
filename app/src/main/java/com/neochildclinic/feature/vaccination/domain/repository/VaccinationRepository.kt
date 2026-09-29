package com.neochildclinic.feature.vaccination.domain.repository

import com.neochildclinic.domain.model.PatientVaccinationCard
import com.neochildclinic.domain.model.VaccinationItem
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.Visit
import kotlinx.coroutines.flow.Flow

interface VaccinationRepository {
    val allVaccinations: Flow<List<Vaccination>>
    fun getVaccinationsForPatient(patientId: String): Flow<List<Vaccination>>
    suspend fun addVaccination(vaccination: Vaccination, transactionGroupId: String? = null): Boolean
    suspend fun refreshVaccinations()
    suspend fun fetchRemoteVaccinationItems(): List<VaccinationItem>
    suspend fun applyDownloadedVaccinationItems(items: List<VaccinationItem>)
    suspend fun transferVaccinations(duplicateId: String, masterId: String)

    suspend fun getVaccinationById(id: String): Vaccination?
    suspend fun deleteVaccination(id: String)
    fun getVaccinationCardsForPatient(patientId: String): Flow<List<PatientVaccinationCard>>
    suspend fun insertVisit(visit: Visit)
    suspend fun updateVisitInventoryStatus(vaccinationId: String, status: String)
}