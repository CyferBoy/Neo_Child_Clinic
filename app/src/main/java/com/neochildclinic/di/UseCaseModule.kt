package com.neochildclinic.di

import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.ConsultationRepository
import com.neochildclinic.domain.repository.VaccinationRepository
import com.neochildclinic.domain.repository.WasteRepository
import com.neochildclinic.domain.usecase.patient.DeletePatientUseCase
import com.neochildclinic.domain.usecase.patient.GetPatientsUseCase
import com.neochildclinic.domain.usecase.patient.SavePatientUseCase
import com.neochildclinic.domain.usecase.sync.RefreshDataUseCase
import com.neochildclinic.domain.usecase.vaccination.DeleteVaccinationUseCase
import com.neochildclinic.domain.usecase.vaccination.GetVaccinationsUseCase
import com.neochildclinic.domain.usecase.vaccination.SaveVaccinationUseCase
import com.neochildclinic.domain.usecase.inventory.ReconcileInventoryUseCase
import com.neochildclinic.data.local.dao.VaccineDao
import com.neochildclinic.data.local.dao.VaccinationDao
import com.neochildclinic.data.local.dao.InventoryDeductionDao
import com.neochildclinic.data.local.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideGetPatientsUseCase(repository: PatientRepository) = GetPatientsUseCase(repository)

    @Provides
    @Singleton
    fun provideSavePatientUseCase(repository: PatientRepository) = SavePatientUseCase(repository)

    @Provides
    @Singleton
    fun provideDeletePatientUseCase(repository: PatientRepository) = DeletePatientUseCase(repository)

    @Provides
    @Singleton
    fun provideGetVaccinationsUseCase(repository: VaccinationRepository) = GetVaccinationsUseCase(repository)

    @Provides
    @Singleton
    fun provideSaveVaccinationUseCase(repository: VaccinationRepository) = SaveVaccinationUseCase(repository)

    @Provides
    @Singleton
    fun provideDeleteVaccinationUseCase(repository: VaccinationRepository) = DeleteVaccinationUseCase(repository)

    @Provides
    @Singleton
    fun provideRefreshDataUseCase(
        patientRepository: PatientRepository,
        vaccinationRepository: VaccinationRepository,
        wasteRepository: WasteRepository,
        inventoryRepository: InventoryRepository,
        reminderRepository: ReminderRepository,
        consultationRepository: ConsultationRepository,
        financeRepository: FinanceRepository
    ) = RefreshDataUseCase(
        patientRepository,
        vaccinationRepository,
        wasteRepository,
        inventoryRepository,
        reminderRepository,
        consultationRepository,
        financeRepository
    )

    @Provides
    @Singleton
    fun provideReconcileInventoryUseCase(
        vaccinationDao: VaccinationDao,
        vaccineDao: VaccineDao,
        inventoryRepository: InventoryRepository,
        inventoryDeductionDao: InventoryDeductionDao,
        database: AppDatabase
    ) = ReconcileInventoryUseCase(vaccinationDao, vaccineDao, inventoryRepository, inventoryDeductionDao, database)
}
