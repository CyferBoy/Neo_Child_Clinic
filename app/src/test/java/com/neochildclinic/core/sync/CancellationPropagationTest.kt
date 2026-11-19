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
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.BackupRepository
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
            patientDao = createProxy(PatientDao::class.java) { _, _ -> null },
            widgetDueDao = createProxy(WidgetDueDao::class.java) { _, _ -> null }
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
            patientDao = createProxy(PatientDao::class.java) { _, _ -> null },
            widgetDueDao = createProxy(WidgetDueDao::class.java) { _, _ -> null }
        )

        assertEquals(ListenableWorker.Result.failure(), worker.doWork())
    }
}
