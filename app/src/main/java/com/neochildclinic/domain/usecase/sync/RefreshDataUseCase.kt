package com.neochildclinic.domain.usecase.sync

import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.domain.repository.VaccinationRepository
import com.neochildclinic.domain.repository.WasteRepository
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.ConsultationRepository
import com.neochildclinic.domain.repository.PersonalReminderRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
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
    private val reminderRepository: ReminderRepository,
    private val consultationRepository: ConsultationRepository,
    private val financeRepository: FinanceRepository,
    private val personalReminderRepository: PersonalReminderRepository
) {
    suspend operator fun invoke() = coroutineScope {
        // 1. Mandatory Order: Patients, Vaccinations, Consultations, then Reminders.
        patientRepository.refreshPatients()
        vaccinationRepository.refreshVaccinations()
        // Finance transactions must be pulled down before the COGS migration below, since
        // that migration patches missing COGS snapshots onto the LOCAL finance rows - if
        // this is skipped (e.g. after the app's local data was cleared), Financial
        // Statistics has nothing to read and shows zero everywhere until this runs.
        financeRepository.refreshTransactions()
        // Remote vaccination records may arrive after the application-startup migration.
        // Re-run the idempotent COGS snapshot migration after refresh so legacy finance rows
        // are upgraded as soon as their linked vaccinations are available locally.
        financeRepository.migrateLegacyVaccinationCogs(vaccinationRepository.allVaccinations.first())
        consultationRepository.refreshConsultations()
        reminderRepository.refreshReminders()
        
        // 2. Inventory must land before vaccination_items. vaccination_items has Room
        // foreign keys to vaccines and vaccine_batches, so importing items in parallel can
        // otherwise permanently skip them on a fresh install.
        val wasteTask = async { wasteRepository.refreshWaste() }
        val inventoryTask = async { inventoryRepository.refreshInventory() }

        wasteTask.await()
        inventoryTask.await()

        // Personal Vaccine Reminders can reference a vaccine_id via a Room foreign key
        // (SET NULL on delete, but still enforced on insert) - must run after inventory
        // above has landed the vaccines table locally, or inserting a reminder that
        // references an as-yet-unknown vaccine would violate that FK and fail silently.
        // This is also what actually populates the feature's local cache in the first
        // place: PersonalReminderScreen only pulls from Room, and previously nothing
        // triggered PersonalReminderRepository.refresh() except a manual pull-to-refresh
        // on that screen, so reminders created outside this device never showed up.
        personalReminderRepository.refresh()

        // Fetch only after the catalog is locally available, then apply in the same refresh.
        // Unresolved dependencies are therefore retried by the next refresh instead of being
        // lost behind an already-completed parallel fetch.
        val vaccinationItems = vaccinationRepository.fetchRemoteVaccinationItems()
        vaccinationRepository.applyDownloadedVaccinationItems(vaccinationItems)
    }
}
