package com.neochildclinic.domain.usecase.vaccination

import com.neochildclinic.domain.manager.VaccinationManager
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.PendingRequirement
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
        requirement: PendingRequirement? = null,
        selectedBatchIds: List<String> = emptyList()
    ): String? {
        return vaccinationManager.completeVaccination(
            vaccination = vaccination,
            user = user,
            isNew = isNew,
            selectedVaccineIds = selectedVaccineIds,
            requirement = requirement,
            selectedBatchIds = selectedBatchIds
        )
    }

    /**
     * Completion from a reminder/requirement (e.g., from Dashboard/Due list).
     */
    suspend fun fromRequirement(
        requirement: PendingRequirement,
        user: String,
        notes: String = ""
    ) {
        vaccinationManager.completeFromRequirement(requirement, user, notes)
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
