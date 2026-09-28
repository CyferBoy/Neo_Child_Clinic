package com.neochildclinic.di

import com.neochildclinic.core.cache.MemoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    @Provides
    @Singleton
    fun providePatientCache(): MemoryCache<String, com.neochildclinic.domain.model.Patient> =
        MemoryCache()

    @Provides
    @Singleton
    fun providePatientListCache(): MemoryCache<com.neochildclinic.core.cache.QueryCacheKey, List<com.neochildclinic.domain.model.Patient>> =
        MemoryCache()

    @Provides
    @Singleton
    fun provideVaccinationCache(): MemoryCache<String, com.neochildclinic.domain.model.Vaccination> =
        MemoryCache()

    @Provides
    @Singleton
    fun provideVaccinationListCache(): MemoryCache<com.neochildclinic.core.cache.QueryCacheKey, List<com.neochildclinic.domain.model.Vaccination>> =
        MemoryCache()

    @Provides
    @Singleton
    fun provideInventoryItemCache(): MemoryCache<String, com.neochildclinic.domain.model.InventoryItem> =
        MemoryCache()

    @Provides
    @Singleton
    fun provideInventoryCache(): MemoryCache<com.neochildclinic.core.cache.QueryCacheKey, List<com.neochildclinic.domain.model.InventoryItem>> =
        MemoryCache()

    @Provides
    @Singleton
    fun provideProfileCache(): MemoryCache<String, com.neochildclinic.domain.model.Profile> =
        MemoryCache()
}
