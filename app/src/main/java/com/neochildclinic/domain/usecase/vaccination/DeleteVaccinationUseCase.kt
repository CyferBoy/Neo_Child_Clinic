package com.neochildclinic.domain.usecase.vaccination

import com.neochildclinic.domain.repository.VaccinationRepository

class DeleteVaccinationUseCase(private val repository: VaccinationRepository) {
    suspend operator fun invoke(id: String) = repository.deleteVaccination(id)
}
