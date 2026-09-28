package com.neochildclinic.di

import com.neochildclinic.data.local.datasource.InventoryLocalDataSource
import com.neochildclinic.data.local.datasource.InventoryLocalDataSourceImpl
import com.neochildclinic.data.local.datasource.PatientLocalDataSource
import com.neochildclinic.data.local.datasource.PatientLocalDataSourceImpl
import com.neochildclinic.data.local.datasource.VaccinationLocalDataSource
import com.neochildclinic.data.local.datasource.VaccinationLocalDataSourceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {
    @Binds abstract fun patientLocalDataSource(impl: PatientLocalDataSourceImpl): PatientLocalDataSource
    @Binds abstract fun vaccinationLocalDataSource(impl: VaccinationLocalDataSourceImpl): VaccinationLocalDataSource
    @Binds abstract fun inventoryLocalDataSource(impl: InventoryLocalDataSourceImpl): InventoryLocalDataSource
}