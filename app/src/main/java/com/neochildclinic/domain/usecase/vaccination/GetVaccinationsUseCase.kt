package com.neochildclinic.domain.usecase.vaccination

import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.repository.VaccinationRepository
import kotlinx.coroutines.flow.Flow

class GetVaccinationsUseCase(private val repository: VaccinationRepository) {
    operator fun invoke(): Flow<List<Vaccination>> = repository.allVaccinations
    
    fun forPatient(patientId: String): Flow<List<Vaccination>> = 
        repository.getVaccinationsForPatient(patientId)
}
