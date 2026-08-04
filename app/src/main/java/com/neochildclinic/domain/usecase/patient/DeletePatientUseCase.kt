package com.neochildclinic.domain.usecase.patient

import com.neochildclinic.domain.repository.PatientRepository

class DeletePatientUseCase(private val repository: PatientRepository) {
    suspend operator fun invoke(id: String) = repository.deletePatient(id)
}
