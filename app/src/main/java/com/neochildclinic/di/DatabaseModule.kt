package com.neochildclinic.di

import android.content.Context
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.dao.PatientDao
import com.neochildclinic.data.local.dao.DueReminderDao
import com.neochildclinic.data.local.dao.ReminderAuditDao
import com.neochildclinic.data.local.dao.AuditLogDao
import com.neochildclinic.data.local.dao.VaccinationDao
import com.neochildclinic.data.local.dao.VaccineDao
import com.neochildclinic.data.local.dao.PatientNotesDao
import com.neochildclinic.data.local.dao.SyncQueueDao
import com.neochildclinic.data.local.dao.WasteDao
import com.neochildclinic.data.local.dao.WidgetDueDao
import com.neochildclinic.data.local.dao.FinanceDao
import com.neochildclinic.data.local.dao.ProfileDao
import com.neochildclinic.data.local.dao.BorrowDao
import com.neochildclinic.data.local.dao.InventoryDeductionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun providePatientDao(database: AppDatabase): PatientDao {
        return database.patientDao()
    }

    @Provides
    fun provideVaccinationDao(database: AppDatabase): VaccinationDao {
        return database.vaccinationDao()
    }

    @Provides
    fun provideDueReminderDao(database: AppDatabase): DueReminderDao {
        return database.dueReminderDao()
    }

    @Provides
    fun provideReminderAuditDao(database: AppDatabase): ReminderAuditDao {
        return database.reminderAuditDao()
    }

    @Provides
    fun provideAuditLogDao(database: AppDatabase): AuditLogDao {
        return database.auditLogDao()
    }

    @Provides
    fun provideVaccineDao(database: AppDatabase): VaccineDao {
        return database.vaccineDao()
    }

    @Provides
    fun providePatientNotesDao(database: AppDatabase): PatientNotesDao {
        return database.patientNotesDao()
    }

    @Provides
    fun provideSyncQueueDao(database: AppDatabase): SyncQueueDao {
        return database.syncQueueDao()
    }

    @Provides
    fun provideWasteDao(database: AppDatabase): WasteDao {
        return database.wasteDao()
    }

    @Provides
    fun provideWidgetDueDao(database: AppDatabase): WidgetDueDao {
        return database.widgetDueDao()
    }

    @Provides
    fun provideFinanceDao(database: AppDatabase): FinanceDao {
        return database.financeDao()
    }

    @Provides
    fun provideProfileDao(database: AppDatabase): ProfileDao {
        return database.profileDao()
    }

    @Provides
    fun provideBorrowDao(database: AppDatabase): BorrowDao {
        return database.borrowDao()
    }

    @Provides
    fun provideInventoryDeductionDao(database: AppDatabase): InventoryDeductionDao {
        return database.inventoryDeductionDao()
    }
}
