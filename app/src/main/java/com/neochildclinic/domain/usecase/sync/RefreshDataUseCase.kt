package com.neochildclinic.domain.usecase.sync

import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.domain.repository.VaccinationRepository
import com.neochildclinic.domain.repository.WasteRepository
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.ReminderRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Use case to trigger a full refresh of local data from remote sources.
 * Coordinates multiple repository refreshes.
 */
class RefreshDataUseCase @Inject constructor(
    private val patientRepository: PatientRepository,
    private val vaccinationRepository: VaccinationRepository,
    private val wasteRepository: WasteRepository,
    private val inventoryRepository: InventoryRepository,
    private val reminderRepository: ReminderRepository
) {
    suspend operator fun invoke() = coroutineScope {
        // 1. Mandatory Order: Patients then Vaccinations then Reminders
        patientRepository.refreshPatients()
        vaccinationRepository.refreshVaccinations()
        reminderRepository.refreshReminders()
        
        // 2. Parallel Independent Tasks
        val wasteTask = async { wasteRepository.refreshWaste() }
        val inventoryTask = async { inventoryRepository.refreshInventory() }
        
        wasteTask.await()
        inventoryTask.await()
    }
}
