package com.neochildclinic.domain.usecase.patient

import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.repository.PatientRepository
import javax.inject.Inject

class GetPatientByIdUseCase @Inject constructor(private val repository: PatientRepository) {
    suspend operator fun invoke(id: String): Patient? = repository.getPatientById(id)
}
