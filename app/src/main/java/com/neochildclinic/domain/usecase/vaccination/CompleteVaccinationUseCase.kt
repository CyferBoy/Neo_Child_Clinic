package com.neochildclinic.domain.usecase.vaccination

import com.neochildclinic.domain.manager.VaccinationManager
import com.neochildclinic.domain.model.Vaccination
import javax.inject.Inject

/**
 * Executes the business logic for completing a vaccination.
 * Delegates coordination to [VaccinationManager] to maintain architectural layers.
 */
class CompleteVaccinationUseCase @Inject constructor(
    private val vaccinationManager: VaccinationManager
) {
    /**
     * Standard completion (e.g., from Add/Edit screen).
     */
    suspend operator fun invoke(
        vaccination: Vaccination, 
        isNew: Boolean,
        selectedVaccineIds: List<String>,
        user: String,
        selectedBatchIds: List<String> = emptyList()
    ): String? {
        return vaccinationManager.completeVaccination(
            vaccination = vaccination,
            user = user,
            isNew = isNew,
            selectedVaccineIds = selectedVaccineIds,
            selectedBatchIds = selectedBatchIds
        )
    }

    /**
     * Completion by marking an existing record as done.
     */
    suspend fun satisfyExisting(
        vaccinationId: String,
        user: String
    ) {
        vaccinationManager.satisfyExistingVaccination(vaccinationId, user)
    }
}
