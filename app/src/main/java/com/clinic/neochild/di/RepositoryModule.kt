package com.clinic.neochild.di

import com.clinic.neochild.data.repository.*
import com.clinic.neochild.domain.repository.*
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
}
