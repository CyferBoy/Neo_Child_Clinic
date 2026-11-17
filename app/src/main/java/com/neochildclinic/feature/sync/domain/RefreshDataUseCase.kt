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

    suspend operator fun invoke(vararg tables: RefreshTables) {
        val key = if (tables.isEmpty()) "ALL" else tables.map { it.name }.sorted().joinToString(",")
        flight.run(key) { runRefresh(stepsFor(tables.toList())) }
    }

    private suspend fun runRefresh(wanted: Set<RefreshStep>?) = coroutineScope {
        // Audit finding 15: one broken pull used to abort every later step (and the error
        // was swallowed by callers). Each step now runs regardless of earlier failures and
        // its failure is collected; a single aggregated error is surfaced at the end.
        val failures = mutableListOf<String>()
        suspend fun step(stepKey: RefreshStep, block: suspend () -> Unit): Boolean {
            if (wanted != null && stepKey !in wanted) return true
            val failure = runRefreshStep(stepKey.displayName, block)
            if (failure != null) {
                failures += failure
                return false
            }
            return true
        }

        step(RefreshStep.PATIENTS) { patientRepository.refreshPatients() }
        step(RefreshStep.VACCINATIONS) { vaccinationRepository.refreshVaccinations() }
        // Finance transactions must be pulled down before the COGS migration below, since
        // that migration patches missing COGS snapshots onto the LOCAL finance rows - if
        // this is skipped (e.g. after the app's local data was cleared), Financial
        // Statistics has nothing to read and shows zero everywhere until this runs.
        step(RefreshStep.FINANCE_TRANSACTIONS) { financeRepository.refreshTransactions() }
        // Expenses has no FK dependencies on any other synced table, so it can refresh
        // independently at any point - placed here alongside the other finance-adjacent
        // pull so Financial Statistics has expense data available as soon as possible.
        step(RefreshStep.EXPENSES) { expenseRepository.refreshExpenses() }
        // Doctor weekly slots / date exceptions have no FK dependency on any other
        // synced table (doctor_id references profiles, already pulled via auth/profile
        // sync elsewhere), so - like expenses - this can refresh independently. Placed
        // before patientTodoRepository.refresh() below so the doctor/slot picker on
        // Today's Patient has fresh availability data as soon as the queue is populated.
        step(RefreshStep.DOCTOR_AVAILABILITY) { doctorAvailabilityRepository.refresh() }
        step(RefreshStep.CONSULTATIONS) { consultationRepository.refreshConsultations() }
        // Today's Patient (consultation_todos/vaccination_todos) previously only ever
        // refreshed once, inside DashboardViewModel's init block - a manual "Sync Now"
        // (SyncViewModel) or any other screen's pull-to-refresh that routes through this
        // use case never picked up a receptionist's newly added entry. Folding it into the
        // same full-refresh pipeline as every other synced entity closes that gap without
        // touching how Today's Patient data is written or displayed.
        step(RefreshStep.TODAYS_PATIENT_TODOS) { patientTodoRepository.refresh() }
        step(RefreshStep.REMINDERS) { reminderRepository.refreshReminders() }

        // 2. Inventory must land before vaccination_items and personal reminders.
        // vaccination_items has Room foreign keys to vaccines and vaccine_batches, and
        // personal reminders can reference vaccine_id.
        // Sequential: SQLite permits only one write transaction at a time; running
        // them sequentially avoids database lock contention.
        step(RefreshStep.INVENTORY) { inventoryRepository.refreshInventory() }
        step(RefreshStep.WASTE) { wasteRepository.refreshWaste() }

        // Personal Vaccine Reminders can reference a vaccine_id via a Room foreign key
        // (SET NULL on delete, but still enforced on insert) - must run after inventory
        // above has landed the vaccines table locally, or inserting a reminder that
        // references an as-yet-unknown vaccine would violate that FK and fail silently.
        // This is also what actually populates the feature's local cache in the first
        // place: PersonalReminderScreen only pulls from Room, and previously nothing
        // triggered PersonalReminderRepositoryImpl.refresh() except a manual pull-to-refresh
        // on that screen, so reminders created outside this device never showed up.
        step(RefreshStep.PERSONAL_REMINDERS) { personalReminderRepository.refresh() }

        // Fetch only after the catalog is locally available, then apply in the same refresh.
        // Unresolved dependencies are therefore retried by the next refresh instead of being
        // lost behind an already-completed parallel fetch. One step: a failed fetch must not
        // proceed to apply an empty/stale list.
        val vaccinationItemsLoaded = step(RefreshStep.VACCINATION_ITEMS) {
            val vaccinationItems = vaccinationRepository.fetchRemoteVaccinationItems()
            vaccinationRepository.applyDownloadedVaccinationItems(vaccinationItems)
        }

        // Remote vaccination records may arrive after the finance pull. Re-run the
        // idempotent COGS snapshot migration only after vaccination items have landed:
        // migrateLegacyVaccinationCogs stamps a permanent zero snapshot for a row whose
        // items are not local yet, and later runs then skip it. If the items step failed,
        // leave COGS alone rather than stamping zeros from a partial dataset.
        if (vaccinationItemsLoaded) {
            step(RefreshStep.COGS_MIGRATION) {
                financeRepository.migrateLegacyVaccinationCogs(vaccinationRepository.allVaccinations.first())
            }
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

/**
 * The single source of truth for pipeline step keys. Both the pipeline and
 * [refreshTablesSteps] use these constants, so renaming one cannot silently desync
 * scoped refresh. [displayName] is the exact string kept in aggregated error messages.
 */
internal enum class RefreshStep(val displayName: String) {
    PATIENTS("Patients"),
    VACCINATIONS("Vaccinations"),
    FINANCE_TRANSACTIONS("Finance transactions"),
    EXPENSES("Expenses"),
    DOCTOR_AVAILABILITY("Doctor availability"),
    CONSULTATIONS("Consultations"),
    TODAYS_PATIENT_TODOS("Today's Patient todos"),
    REMINDERS("Reminders"),
    INVENTORY("Inventory"),
    WASTE("Waste"),
    PERSONAL_REMINDERS("Personal reminders"),
    VACCINATION_ITEMS("Vaccination items"),
    COGS_MIGRATION("COGS migration")
}

/**
 * Which tables a [RefreshDataUseCase] pull covers. Full-scope callers pass nothing;
 * realtime/screen-entry triggers pass only the tables they actually show, so a pushed
 * change cannot re-pull the entire database from this device. Scopes include their FK
 * dependencies (e.g. VISITS pulls Patients first, exactly like the ordering comments
 * in the pipeline above), so a scoped pull never inserts rows Room would reject.
 */
enum class RefreshTables {
    PATIENTS, VISITS, FINANCE, EXPENSES, CONSULTATIONS, TODOS, REMINDERS,
    WASTE, INVENTORY, PERSONAL_REMINDERS, VACCINATION_ITEMS
}

// Steps each table needs, including FK dependencies. Order exists only in the comments
// above (see RefreshDataUseCase); the pipeline runs its steps in pipeline order and skips
// the rest. RefreshStep is the shared key type - RefreshTablesTest guards that every step
// used by the pipeline appears here.
internal val refreshTablesSteps: Map<RefreshTables, List<RefreshStep>> = mapOf(
    RefreshTables.PATIENTS to listOf(RefreshStep.PATIENTS),
    RefreshTables.VISITS to listOf(RefreshStep.PATIENTS, RefreshStep.VACCINATIONS),
    RefreshTables.FINANCE to listOf(
        RefreshStep.PATIENTS, RefreshStep.VACCINATIONS, RefreshStep.FINANCE_TRANSACTIONS,
        RefreshStep.INVENTORY, RefreshStep.VACCINATION_ITEMS, RefreshStep.COGS_MIGRATION
    ),
    RefreshTables.EXPENSES to listOf(RefreshStep.EXPENSES),
    RefreshTables.CONSULTATIONS to listOf(
        RefreshStep.PATIENTS, RefreshStep.VACCINATIONS, RefreshStep.CONSULTATIONS
    ),
    // Doctor availability feeds the doctor/slot picker on Today's Patient (see pipeline
    // comment), so the TODOS scope must pull it along with the todo rows.
    RefreshTables.TODOS to listOf(
        RefreshStep.PATIENTS, RefreshStep.DOCTOR_AVAILABILITY, RefreshStep.TODAYS_PATIENT_TODOS
    ),
    RefreshTables.REMINDERS to listOf(RefreshStep.PATIENTS, RefreshStep.REMINDERS),
    RefreshTables.WASTE to listOf(RefreshStep.WASTE),
    RefreshTables.INVENTORY to listOf(RefreshStep.INVENTORY),
    RefreshTables.PERSONAL_REMINDERS to listOf(
        RefreshStep.INVENTORY, RefreshStep.PERSONAL_REMINDERS
    ),
    RefreshTables.VACCINATION_ITEMS to listOf(
        RefreshStep.INVENTORY, RefreshStep.VACCINATION_ITEMS
    )
)

// null = every step (an unscooped call must behave exactly as before this API existed).
internal fun stepsFor(tables: List<RefreshTables>): Set<RefreshStep>? =
    if (tables.isEmpty()) null
    else tables.flatMap { refreshTablesSteps.getValue(it) }.toSet()

