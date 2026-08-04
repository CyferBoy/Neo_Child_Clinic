package com.neochildclinic.domain.usecase.patient

import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.repository.PatientRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Searches for patients using optimized database queries.
 * Supports searching by patient details, vaccine names, and receipt numbers.
 */
class SearchPatientsUseCase @Inject constructor(
    private val patientRepository: PatientRepository
) {
    operator fun invoke(query: String): Flow<List<Patient>> {
        return if (query.isBlank()) {
            patientRepository.allPatients
        } else {
            patientRepository.searchPatients("%$query%")
        }
    }
}
