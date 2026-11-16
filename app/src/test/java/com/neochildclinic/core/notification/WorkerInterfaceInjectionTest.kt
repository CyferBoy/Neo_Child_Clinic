package com.neochildclinic.core.notification

import com.neochildclinic.app.work.DailySummaryWorker
import com.neochildclinic.app.work.SyncWorker
import com.neochildclinic.data.local.dao.PersonalReminderDao
import com.neochildclinic.feature.inventory.domain.repository.InventoryRepository
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adversarial verification that background workers depend strictly on interface abstractions
 * rather than concrete repository implementations or raw AppDatabase (UNTANGLE-07).
 */
class WorkerInterfaceInjectionTest {

    @Test
    fun dailySummaryWorkerInjectsInventoryRepositoryInterface() {
        val constructor = DailySummaryWorker::class.java.constructors.first()
        val paramTypes = constructor.parameterTypes

        assertTrue(
            "DailySummaryWorker must depend on InventoryRepository interface",
            paramTypes.any { it == InventoryRepository::class.java }
        )
        assertFalse(
            "DailySummaryWorker must NOT depend on concrete InventoryRepositoryImpl",
            paramTypes.any { it.name.contains("InventoryRepositoryImpl") }
        )
    }

    @Test
    fun syncWorkerInjectsSyncRepositoryInterface() {
        val constructor = SyncWorker::class.java.constructors.first()
        val paramTypes = constructor.parameterTypes

        assertTrue(
            "SyncWorker must depend on SyncRepository interface",
            paramTypes.any { it == SyncRepository::class.java }
        )
        assertFalse(
            "SyncWorker must NOT depend on concrete SyncRepositoryImpl",
            paramTypes.any { it.name.contains("SyncRepositoryImpl") }
        )
    }

    @Test
    fun personalReminderWorkerInjectsPersonalReminderDaoAbstraction() {
        val constructor = PersonalReminderNotificationWorker::class.java.constructors.first()
        val paramTypes = constructor.parameterTypes

        assertTrue(
            "PersonalReminderNotificationWorker must depend on PersonalReminderDao",
            paramTypes.any { it == PersonalReminderDao::class.java }
        )
        assertFalse(
            "PersonalReminderNotificationWorker must NOT depend on raw AppDatabase",
            paramTypes.any { it.name.contains("AppDatabase") }
        )
    }
}
