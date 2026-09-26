package com.neochildclinic.domain.repository

import com.neochildclinic.data.local.entity.VaccinationItemEntity
import com.neochildclinic.domain.model.Vaccination
import kotlinx.coroutines.flow.Flow

// ponytail: item sync passes the Room DTO (VaccinationItemEntity) straight through - it only
// carries id/vaccineId/batch refs between RefreshDataUseCase and the data layer, and there is
// no independent domain model for a vaccination line item. Add one only if a business rule
// starts reading items outside the data layer.
interface VaccinationRepository {
    val allVaccinations: Flow<List<Vaccination>>
    fun getVaccinationsForPatient(patientId: String): Flow<List<Vaccination>>
    suspend fun addVaccination(vaccination: Vaccination, transactionGroupId: String? = null): Boolean
    suspend fun refreshVaccinations()
    suspend fun fetchRemoteVaccinationItems(): List<VaccinationItemEntity>
    suspend fun applyDownloadedVaccinationItems(items: List<VaccinationItemEntity>)
    suspend fun transferVaccinations(duplicateId: String, masterId: String)
}