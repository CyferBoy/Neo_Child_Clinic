package com.neochildclinic.di

import com.neochildclinic.data.repository.*
import com.neochildclinic.data.manager.SyncManagerImpl
import com.neochildclinic.domain.manager.SyncManager
import com.neochildclinic.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPatientRepository(impl: PatientRepositoryImpl): PatientRepository

    @Binds
    @Singleton
    abstract fun bindVaccinationRepository(impl: VaccinationRepositoryImpl): VaccinationRepository

    @Binds
    @Singleton
    abstract fun bindReminderRepository(impl: ReminderRepositoryImpl): ReminderRepository

    @Binds
    @Singleton
    abstract fun bindInventoryRepository(impl: InventoryRepositoryImpl): InventoryRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds
    @Singleton
    abstract fun bindSyncManager(impl: SyncManagerImpl): SyncManager

    @Binds
    @Singleton
    abstract fun bindWasteRepository(impl: WasteRepositoryImpl): WasteRepository

    @Binds
    @Singleton
    abstract fun bindFinanceRepository(impl: FinanceRepositoryImpl): FinanceRepository

    @Binds
    @Singleton
    abstract fun bindConsultationRepository(impl: ConsultationRepositoryImpl): ConsultationRepository

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindDashboardRepository(impl: DashboardRepositoryImpl): DashboardRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository
}
