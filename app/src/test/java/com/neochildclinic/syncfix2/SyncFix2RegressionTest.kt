package com.neochildclinic.syncfix2

import com.neochildclinic.core.sync.RefreshDataUseCase
import com.neochildclinic.core.sync.RefreshTables
import com.neochildclinic.domain.repository.ConsultationRepository
import com.neochildclinic.domain.repository.DoctorAvailabilityRepository
import com.neochildclinic.domain.repository.ExpenseRepository
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.domain.repository.PatientTodoRepository
import com.neochildclinic.domain.repository.PersonalReminderRepository
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.VaccinationRepository
import com.neochildclinic.domain.repository.WasteRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.util.Collections
import kotlin.coroutines.Continuation

/**
 * Regressions for the sync fixes: one shared refresh flight key, and the scoped FAILED-row
 * DAO query used by enqueue coalescing. No MockK/Robolectric in this module - interfaces are
 * faked with a java.lang.reflect.Proxy and the SQL/usage is checked against the source.
 */
class SyncFix2RegressionTest {

    @Suppress("UNCHECKED_CAST")
    private fun <T> fake(clazz: Class<T>, handler: (String, Array<Any?>?) -> Any?): T =
        Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz)) { _, method, args ->
            handler(method.name, args)
        } as T

    @Test
    fun `refresh calls with different table scopes coalesce onto one run`() = runBlocking {
        // The owner parks inside refreshPatients() while holding the shared flight.
        val patientsStarted = CompletableDeferred<Unit>()
        val ownerContinuations = Collections.synchronizedList(mutableListOf<Continuation<Unit>>())
        val patientRepo = fake(PatientRepository::class.java) { name, args ->
            if (name == "refreshPatients") {
                patientsStarted.complete(Unit)
                @Suppress("UNCHECKED_CAST")
                ownerContinuations.add(args!!.last() as Continuation<Unit>)
                COROUTINE_SUSPENDED
            } else null
        }
        var wasteCalls = 0
        val wasteRepo = fake(WasteRepository::class.java) { name, _ ->
            if (name == "refreshWaste") wasteCalls++
            null
        }

        val useCase = RefreshDataUseCase(
            patientRepository = patientRepo,
            vaccinationRepository = fake(VaccinationRepository::class.java) { _, _ -> null },
            wasteRepository = wasteRepo,
            inventoryRepository = fake(InventoryRepository::class.java) { _, _ -> null },
            reminderRepository = fake(ReminderRepository::class.java) { _, _ -> null },
            consultationRepository = fake(ConsultationRepository::class.java) { _, _ -> null },
            patientTodoRepository = fake(PatientTodoRepository::class.java) { _, _ -> null },
            financeRepository = fake(FinanceRepository::class.java) { _, _ -> null },
            personalReminderRepository = fake(PersonalReminderRepository::class.java) { _, _ -> null },
            expenseRepository = fake(ExpenseRepository::class.java) { _, _ -> null },
            doctorAvailabilityRepository = fake(DoctorAvailabilityRepository::class.java) { _, _ -> null }
        )

        val owner = launch(Dispatchers.Default) { useCase.invoke(RefreshTables.PATIENTS) }
        withTimeout(1_000) { patientsStarted.await() }

        val coalesced = launch(Dispatchers.Default) { useCase.invoke(RefreshTables.WASTE) }
        // With the old table-set key the WASTE call starts its own run and finishes at once;
        // with one shared key it can only finish after the owner is released.
        val finishedEarly = withTimeoutOrNull(300) { coalesced.join(); true } ?: false

        assertFalse("a differently-scoped refresh must coalesce, not start a second run", finishedEarly)
        assertEquals("coalesced caller must not run its own WASTE step", 0, wasteCalls)

        ownerContinuations.forEach { it.resumeWith(Result.success(Unit)) }
        joinAll(owner, coalesced)
    }

    @Test
    fun `scoped failed query exists and enqueue stops filtering every failed row`() {
        val dao = readSource("data/local/dao/SyncQueueDao.kt")
        assertTrue(
            "DAO must expose a scoped FAILED lookup by entity name/id",
            dao.contains("getFailedForEntity") &&
                dao.contains("status = 'FAILED'") &&
                dao.contains("entityName = :name") &&
                dao.contains("entityId = :id")
        )
        assertTrue("pending batch query must be bounded with LIMIT", dao.contains("LIMIT :limit"))

        val repo = readSource("feature/sync/data/SyncRepositoryImpl.kt")
        val enqueue = repo.substringAfter("override suspend fun enqueue(")
            .substringBefore("override fun getPendingCount")
        assertTrue("enqueue must use the scoped failed query", enqueue.contains("getFailedForEntity(entityName, entityId)"))
        assertFalse(
            "enqueue must not load + filter all FAILED rows in Kotlin",
            enqueue.contains("getItemsByStatus(SyncStatus.FAILED.name).filter")
        )
    }

    private fun readSource(relativePath: String): String {
        var dir = File(System.getProperty("user.dir"))
        repeat(6) {
            val candidate = File(dir, "app/src/main/java/com/neochildclinic")
            if (candidate.isDirectory) return File(candidate, relativePath).readText()
            dir = dir.parentFile ?: return error("Could not locate main source root")
        }
        error("Could not locate main source root")
    }
}
