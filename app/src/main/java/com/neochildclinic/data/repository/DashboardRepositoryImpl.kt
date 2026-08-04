package com.neochildclinic.data.repository

import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.domain.repository.*
import com.neochildclinic.core.utils.DateClassifier
import com.neochildclinic.core.utils.DateCategory
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashboardRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val inventoryRepository: InventoryRepository,
    private val reminderRepository: ReminderRepository,
    private val wasteRepository: WasteRepository,
    private val syncRepository: SyncRepository
) : DashboardRepository {

    private val patientDao = database.patientDao()
    private val borrowDao = database.borrowDao()

    override fun getPatientCount(): Flow<Int> = patientDao.getPatientCount()

    override fun getLowStockCount(): Flow<Int> {
        return inventoryRepository.getInventoryItems().map { items ->
            items.count { it.isLowStock }
        }
    }

    override fun getBorrowedCount(): Flow<Int> {
        return borrowDao.getActiveBorrows().map { it.size }
    }

    override fun getDueCount(): Flow<Int> {
        return reminderRepository.getDueList().map { list ->
            val todayCal = DateClassifier.getTodayStart()
            list.count { 
                val cat = DateClassifier.classify(it.nextDueDate, todayCal)
                cat is DateCategory.Today || cat is DateCategory.GracePeriod || cat is DateCategory.Yesterday
            }
        }
    }

    override fun getWasteCount(): Flow<Int> = wasteRepository.getWasteCount()

    override suspend fun refreshDashboardData() {
        syncRepository.processNextItems()
    }
}
