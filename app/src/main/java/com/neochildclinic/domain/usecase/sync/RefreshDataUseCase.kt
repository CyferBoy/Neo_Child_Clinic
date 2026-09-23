package com.neochildclinic.domain.usecase.sync

import com.neochildclinic.data.repository.PatientRepositoryImpl
import com.neochildclinic.data.repository.FinanceRepositoryImpl
import com.neochildclinic.data.repository.VaccinationRepositoryImpl
import com.neochildclinic.data.repository.WasteRepositoryImpl
import com.neochildclinic.data.repository.InventoryRepositoryImpl
import com.neochildclinic.data.repository.ReminderRepositoryImpl
import com.neochildclinic.data.repository.ConsultationRepositoryImpl
import com.neochildclinic.data.repository.PatientTodoRepositoryImpl
import com.neochildclinic.data.repository.PersonalReminderRepositoryImpl
import com.neochildclinic.data.repository.ExpenseRepositoryImpl
import com.neochildclinic.data.repository.DoctorAvailabilityRepositoryImpl
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case to trigger a full refresh of local data from remote sources.
 * Coordinates multiple repository refreshes.
 */
class RefreshDataUseCase @Inject constructor(
    private val patientRepository: PatientRepositoryImpl,
    private val vaccinationRepository: VaccinationRepositoryImpl,
    private val wasteRepository: WasteRepositoryImpl,
    private val inventoryRepository: InventoryRepositoryImpl,
    private val reminderRepository: ReminderRepositoryImpl,
    private val consultationRepository: ConsultationRepositoryImpl,
    private val patientTodoRepository: PatientTodoRepositoryImpl,
    private val financeRepository: FinanceRepositoryImpl,
    private val personalReminderRepository: PersonalReminderRepositoryImpl,
    private val expenseRepository: ExpenseRepositoryImpl,
    private val doctorAvailabilityRepository: DoctorAvailabilityRepositoryImpl
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
        // Expenses has no FK dependencies on any other synced table, so it can refresh
        // independently at any point - placed here alongside the other finance-adjacent
        // pull so Financial Statistics has expense data available as soon as possible.
        expenseRepository.refreshExpenses()
        // Doctor weekly slots / date exceptions have no FK dependency on any other
        // synced table (doctor_id references profiles, already pulled via auth/profile
        // sync elsewhere), so - like expenses - this can refresh independently. Placed
        // before patientTodoRepository.refresh() below so the doctor/slot picker on
        // Today's Patient has fresh availability data as soon as the queue is populated.
        doctorAvailabilityRepository.refresh()
        // Remote vaccination records may arrive after the application-startup migration.
        // Re-run the idempotent COGS snapshot migration after refresh so legacy finance rows
        // are upgraded as soon as their linked vaccinations are available locally.
        financeRepository.migrateLegacyVaccinationCogs(vaccinationRepository.allVaccinations.first())
        consultationRepository.refreshConsultations()
        // Today's Patient (consultation_todos/vaccination_todos) previously only ever
        // refreshed once, inside DashboardViewModel's init block - a manual "Sync Now"
        // (SyncViewModel) or any other screen's pull-to-refresh that routes through this
        // use case never picked up a receptionist's newly added entry. Folding it into the
        // same full-refresh pipeline as every other synced entity closes that gap without
        // touching how Today's Patient data is written or displayed.
        patientTodoRepository.refresh()
        reminderRepository.refreshReminders()
        
        // 2. Inventory must land before vaccination_items. vaccination_items has Room
        // foreign keys to vaccines and vaccine_batches, so importing items in parallel can
        // otherwise permanently skip them on a fresh install.
        // Parallel: waste and inventory have no FK dependency on each other.
        kotlinx.coroutines.awaitAll(
            async { wasteRepository.refreshWaste() },
            async { inventoryRepository.refreshInventory() }
        )

        // Personal Vaccine Reminders can reference a vaccine_id via a Room foreign key
        // (SET NULL on delete, but still enforced on insert) - must run after inventory
        // above has landed the vaccines table locally, or inserting a reminder that
        // references an as-yet-unknown vaccine would violate that FK and fail silently.
        // This is also what actually populates the feature's local cache in the first
        // place: PersonalReminderScreen only pulls from Room, and previously nothing
        // triggered PersonalReminderRepositoryImpl.refresh() except a manual pull-to-refresh
        // on that screen, so reminders created outside this device never showed up.
        personalReminderRepository.refresh()

        // Fetch only after the catalog is locally available, then apply in the same refresh.
        // Unresolved dependencies are therefore retried by the next refresh instead of being
        // lost behind an already-completed parallel fetch.
        val vaccinationItems = vaccinationRepository.fetchRemoteVaccinationItems()
        vaccinationRepository.applyDownloadedVaccinationItems(vaccinationItems)
    }
}
