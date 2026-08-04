package com.neochildclinic.domain.usecase.vaccination

import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.repository.VaccinationRepository

class SaveVaccinationUseCase(private val repository: VaccinationRepository) {
    suspend operator fun invoke(vaccination: Vaccination) = repository.addVaccination(vaccination)
}
