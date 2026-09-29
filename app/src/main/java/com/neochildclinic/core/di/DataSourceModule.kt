package com.neochildclinic.core.di

import com.neochildclinic.feature.inventory.data.InventoryLocalDataSource
import com.neochildclinic.feature.inventory.data.InventoryLocalDataSourceImpl
import com.neochildclinic.feature.patient.data.PatientLocalDataSource
import com.neochildclinic.feature.patient.data.PatientLocalDataSourceImpl
import com.neochildclinic.feature.vaccination.data.VaccinationLocalDataSource
import com.neochildclinic.feature.vaccination.data.VaccinationLocalDataSourceImpl
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