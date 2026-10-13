package com.neochildclinic.feature.sync.domain

import com.neochildclinic.feature.patient.domain.repository.PatientRepository
import com.neochildclinic.feature.finance.domain.repository.FinanceRepository
import com.neochildclinic.feature.vaccination.domain.repository.VaccinationRepository
import com.neochildclinic.feature.waste.domain.repository.WasteRepository
import com.neochildclinic.feature.inventory.domain.repository.InventoryRepository
import com.neochildclinic.feature.reminder.domain.repository.ReminderRepository
import com.neochildclinic.feature.consultation.domain.repository.ConsultationRepository
import com.neochildclinic.feature.patient.domain.repository.PatientTodoRepository
import com.neochildclinic.feature.personalreminder.domain.repository.PersonalReminderRepository
import com.neochildclinic.feature.finance.domain.repository.ExpenseRepository
import com.neochildclinic.feature.doctor.domain.repository.DoctorAvailabilityRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import com.neochildclinic.core.sync.SingleFlight

/**
 * Use case to trigger a full refresh of local data from remote sources.
 * Coordinates multiple repository refreshes.
 *
 * @Singleton so the [flight] gate is shared by every caller: previously nine ViewModels
 * could run this 11-repository pipeline concurrently, which both wasted network and -
 * because refreshVaccinations skips visits whose patient is not local yet - made
 * vaccination records land in Room across several overlapping runs (the "records keep
 * appearing over time" symptom). Concurrent callers now wait for the one in-flight run
 * instead of starting a second one.
 */
@Singleton
class RefreshDataUseCase @Inject constructor(
    private val patientRepository: PatientRepository,
    private val vaccinationRepository: VaccinationRepository,
    private val wasteRepository: WasteRepository,
    private val inventoryRepository: InventoryRepository,
    private val reminderRepository: ReminderRepository,
    private val consultationRepository: ConsultationRepository,
    private val patientTodoRepository: PatientTodoRepository,
    private val financeRepository: FinanceRepository,
    private val personalReminderRepository: PersonalReminderRepository,
    private val expenseRepository: ExpenseRepository,
    private val doctorAvailabilityRepository: DoctorAvailabilityRepository
) {
    private val flight = SingleFlight()

    suspend operator fun invoke() = flight.run { runRefresh() }

    private suspend fun runRefresh() = coroutineScope {
        // Audit finding 15: one broken pull used to abort every later step (and the error
        // was swallowed by callers). Each step now runs regardless of earlier failures and
        // its failure is collected; a single aggregated error is surfaced at the end.
        val failures = mutableListOf<String>()
        suspend fun step(name: String, block: suspend () -> Unit) {
            runRefreshStep(name, block)?.let { failures += it }
        }

        // 1. Mandatory Order: Patients, Vaccinations, Consultations, then Reminders.
        step("Patients") { patientRepository.refreshPatients() }
        step("Vaccinations") { vaccinationRepository.refreshVaccinations() }
        // Finance transactions must be pulled down before the COGS migration below, since
        // that migration patches missing COGS snapshots onto the LOCAL finance rows - if
        // this is skipped (e.g. after the app's local data was cleared), Financial
        // Statistics has nothing to read and shows zero everywhere until this runs.
        step("Finance transactions") { financeRepository.refreshTransactions() }
        // Expenses has no FK dependencies on any other synced table, so it can refresh
        // independently at any point - placed here alongside the other finance-adjacent
        // pull so Financial Statistics has expense data available as soon as possible.
        step("Expenses") { expenseRepository.refreshExpenses() }
        // Doctor weekly slots / date exceptions have no FK dependency on any other
        // synced table (doctor_id references profiles, already pulled via auth/profile
        // sync elsewhere), so - like expenses - this can refresh independently. Placed
        // before patientTodoRepository.refresh() below so the doctor/slot picker on
        // Today's Patient has fresh availability data as soon as the queue is populated.
        step("Doctor availability") { doctorAvailabilityRepository.refresh() }
        // Remote vaccination records may arrive after the application-startup migration.
        // Re-run the idempotent COGS snapshot migration after refresh so legacy finance rows
        // are upgraded as soon as their linked vaccinations are available locally.
        step("COGS migration") {
            financeRepository.migrateLegacyVaccinationCogs(vaccinationRepository.allVaccinations.first())
        }
        step("Consultations") { consultationRepository.refreshConsultations() }
        // Today's Patient (consultation_todos/vaccination_todos) previously only ever
        // refreshed once, inside DashboardViewModel's init block - a manual "Sync Now"
        // (SyncViewModel) or any other screen's pull-to-refresh that routes through this
        // use case never picked up a receptionist's newly added entry. Folding it into the
        // same full-refresh pipeline as every other synced entity closes that gap without
        // touching how Today's Patient data is written or displayed.
        step("Today's Patient todos") { patientTodoRepository.refresh() }
        step("Reminders") { reminderRepository.refreshReminders() }

        // 2. Inventory must land before vaccination_items. vaccination_items has Room
        // foreign keys to vaccines and vaccine_batches, so importing items in parallel can
        // otherwise permanently skip them on a fresh install.
        // Parallel: waste and inventory have no FK dependency on each other.
        kotlinx.coroutines.awaitAll(
            async { step("Waste") { wasteRepository.refreshWaste() } },
            async { step("Inventory") { inventoryRepository.refreshInventory() } }
        )

        // Personal Vaccine Reminders can reference a vaccine_id via a Room foreign key
        // (SET NULL on delete, but still enforced on insert) - must run after inventory
        // above has landed the vaccines table locally, or inserting a reminder that
        // references an as-yet-unknown vaccine would violate that FK and fail silently.
        // This is also what actually populates the feature's local cache in the first
        // place: PersonalReminderScreen only pulls from Room, and previously nothing
        // triggered PersonalReminderRepositoryImpl.refresh() except a manual pull-to-refresh
        // on that screen, so reminders created outside this device never showed up.
        step("Personal reminders") { personalReminderRepository.refresh() }

        // Fetch only after the catalog is locally available, then apply in the same refresh.
        // Unresolved dependencies are therefore retried by the next refresh instead of being
        // lost behind an already-completed parallel fetch. One step: a failed fetch must not
        // proceed to apply an empty/stale list.
        step("Vaccination items") {
            val vaccinationItems = vaccinationRepository.fetchRemoteVaccinationItems()
            vaccinationRepository.applyDownloadedVaccinationItems(vaccinationItems)
        }

        if (failures.isNotEmpty()) {
            // One error for the whole pipeline: every failed step is named, so the caller
            // can tell what was skipped instead of getting a silent partial refresh.
            throw IllegalStateException("Cloud refresh failed: ${failures.joinToString()}")
        }
    }
}

// Runs one pipeline step without letting its failure abort the remaining steps. Returns
// null on success, or a "name: reason" description for the aggregated error thrown at the
// end of the pipeline. Cancellation is rethrown: a cancelled refresh must stop the
// pipeline immediately rather than be recorded as an ordinary step failure.
internal suspend fun runRefreshStep(name: String, block: suspend () -> Unit): String? =
    try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        "$name: ${e.message ?: e::class.java.simpleName}"
    }

