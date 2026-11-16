package com.neochildclinic.di

import com.neochildclinic.data.remote.PatientRemoteDataSource
import com.neochildclinic.data.remote.PatientRemoteDataSourceImpl
import com.neochildclinic.feature.inventory.data.InventoryRemoteDataSource
import com.neochildclinic.feature.inventory.data.InventoryRemoteDataSourceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteDataSourceModule {
    @Binds abstract fun patientRemoteDataSource(impl: PatientRemoteDataSourceImpl): PatientRemoteDataSource
    @Binds abstract fun inventoryRemoteDataSource(impl: InventoryRemoteDataSourceImpl): InventoryRemoteDataSource
}
