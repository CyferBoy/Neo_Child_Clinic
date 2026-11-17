package com.neochildclinic.core.sync

import android.content.Context
import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.taskexecutor.SerialExecutor
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import com.google.common.util.concurrent.ListenableFuture
import com.neochildclinic.data.backup.AutoBackupWorker
import com.neochildclinic.data.local.dao.*
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.domain.model.BackupOperationResult
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.feature.reminder.domain.repository.ReminderRepository
import com.neochildclinic.feature.settings.domain.repository.BackupRepository
import com.neochildclinic.feature.widget.data.WidgetWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.Executor

/**
 * Cancellation propagation through background workers, driven through their public
 * constructors and public doWork() API. No Unsafe allocation and no reflective field writes.
 */
class CancellationPropagationTest {

    private fun testWorkerParams(): WorkerParameters {
        val executor = Executor { command -> command.run() }
        val serial = object : SerialExecutor {
            override fun execute(command: Runnable) { command.run() }
            override fun hasPendingTasks(): Boolean = false
        }
        val taskExecutor = object : TaskExecutor {
            override fun getMainThreadExecutor(): Executor = executor
            override fun getSerialTaskExecutor(): SerialExecutor = serial
        }
        val factory = object : WorkerFactory() {
            override fun createWorker(
                context: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = throw UnsupportedOperationException("not used in unit tests")
        }
        val progress = object : ProgressUpdater {
            override fun updateProgress(context: Context, workerId: UUID, progress: Data): ListenableFuture<Void> =
                throw UnsupportedOperationException("not used in unit tests")
        }
        val foreground = object : ForegroundUpdater {
            override fun setForegroundAsync(context: Context, workerId: UUID, foregroundInfo: ForegroundInfo): ListenableFuture<Void> =
                throw UnsupportedOperationException("not used in unit tests")
        }
        return WorkerParameters(
            UUID.randomUUID(), Data.EMPTY, emptyList(), WorkerParameters.RuntimeExtras(),
            0, 0, executor, kotlin.coroutines.EmptyCoroutineContext,
            taskExecutor, factory, progress, foreground
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> createProxy(clazz: Class<T>, handler: (name: String, args: Array<Any?>?) -> Any?): T =
        Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz)) { _, method, args ->
            handler(method.name, args)
        } as T

    // AppDatabase is abstract, not an interface, so a minimal concrete stub is needed just to
    // satisfy WidgetWorker's constructor. doWork() throws before it touches any DAO in these tests.
    private class StubAppDatabase : AppDatabase() {
        override fun patientDao(): PatientDao = throw UnsupportedOperationException()
        override fun vaccinationDao(): VaccinationDao = throw UnsupportedOperationException()
        override fun dueReminderDao(): DueReminderDao = throw UnsupportedOperationException()
        override fun vaccineDao(): VaccineDao = throw UnsupportedOperationException()
        override fun syncQueueDao(): SyncQueueDao = throw UnsupportedOperationException()
        override fun wasteDao(): WasteDao = throw UnsupportedOperationException()
        override fun widgetDueDao(): WidgetDueDao = throw UnsupportedOperationException()
        override fun auditLogDao(): AuditLogDao = throw UnsupportedOperationException()
        override fun financeDao(): FinanceDao = throw UnsupportedOperationException()
        override fun profileDao(): ProfileDao = throw UnsupportedOperationException()
        override fun borrowDao(): BorrowDao = throw UnsupportedOperationException()
        override fun patientNotesDao(): PatientNotesDao = throw UnsupportedOperationException()
        override fun inventoryDeductionDao(): InventoryDeductionDao = throw UnsupportedOperationException()
        override fun consultationDao(): ConsultationDao = throw UnsupportedOperationException()
        override fun vaccinationItemDao(): VaccinationItemDao = throw UnsupportedOperationException()
        override fun patientTodoDao(): PatientTodoDao = throw UnsupportedOperationException()
        override fun personalReminderDao(): PersonalReminderDao = throw UnsupportedOperationException()
        override fun borrowReturnDao(): BorrowReturnDao = throw UnsupportedOperationException()
        override fun expenseDao(): ExpenseDao = throw UnsupportedOperationException()
        override fun doctorAvailabilityDao(): DoctorAvailabilityDao = throw UnsupportedOperationException()
        override fun backupDao(): BackupDao = throw UnsupportedOperationException()
        override fun createInvalidationTracker(): InvalidationTracker = InvalidationTracker(this, emptyMap(), emptyMap(), "dummy")
        override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper = throw UnsupportedOperationException()
        override fun clearAllTables() = throw UnsupportedOperationException()
    }

    private fun backupRepo(handler: (String) -> Any?) = createProxy(BackupRepository::class.java) { name, _ -> handler(name) }
    private fun reminderRepo(handler: (String) -> Any?) = createProxy(ReminderRepository::class.java) { name, _ -> handler(name) }

    @Test
    fun autoBackupWorkerCleanlyRethrowsCancellationException() = runBlocking {
        val worker = AutoBackupWorker(
            appContext = android.content.ContextWrapper(null),
            workerParams = testWorkerParams(),
            backupRepository = backupRepo { name ->
                if (name == "performAutomaticBackup") throw CancellationException("AutoBackup job cancelled by OS") else null
            }
        )

        try {
            worker.doWork()
            fail("AutoBackupWorker.doWork() must throw CancellationException")
        } catch (e: CancellationException) {
            assertEquals("AutoBackup job cancelled by OS", e.message)
        }
    }

    @Test
    fun autoBackupWorkerReturnsRetryOnNonCancellationException() = runBlocking {
        val worker = AutoBackupWorker(
            appContext = android.content.ContextWrapper(null),
            workerParams = testWorkerParams(),
            backupRepository = backupRepo { name ->
                if (name == "performAutomaticBackup") throw RuntimeException("Storage unavailable") else null
            }
        )

        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
    }

    @Test
    fun autoBackupWorkerReturnsSuccessOnSuccessfulBackup() = runBlocking {
        val worker = AutoBackupWorker(
            appContext = android.content.ContextWrapper(null),
            workerParams = testWorkerParams(),
            backupRepository = backupRepo { name ->
                if (name == "performAutomaticBackup") {
                    BackupOperationResult(success = true, backupId = "backup-123")
                } else null
            }
        )

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }

    @Test
    fun widgetWorkerCleanlyRethrowsCancellationException() = runBlocking {
        val worker = WidgetWorker(
            context = android.content.ContextWrapper(null),
            params = testWorkerParams(),
            reminderRepository = reminderRepo { name ->
                if (name == "getDueList") {
                    flow<List<Vaccination>> { throw CancellationException("Widget worker coroutine cancelled") }
                } else null
            },
            database = StubAppDatabase()
        )

        try {
            worker.doWork()
            fail("WidgetWorker.doWork() must throw CancellationException")
        } catch (e: CancellationException) {
            assertEquals("Widget worker coroutine cancelled", e.message)
        }
    }

    @Test
    fun widgetWorkerReturnsFailureOnGeneralException() = runBlocking {
        val worker = WidgetWorker(
            context = android.content.ContextWrapper(null),
            params = testWorkerParams(),
            reminderRepository = reminderRepo { name ->
                if (name == "getDueList") {
                    flow<List<Vaccination>> { throw RuntimeException("Database error") }
                } else null
            },
            database = StubAppDatabase()
        )

        assertEquals(ListenableWorker.Result.failure(), worker.doWork())
    }
}
