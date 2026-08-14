package com.neochildclinic.di

import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.PatientRepository
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
import com.neochildclinic.domain.usecase.inventory.BackfillInventoryUsageUseCase
import com.neochildclinic.domain.usecase.inventory.ReconcileInventoryUseCase
import com.neochildclinic.data.local.dao.VaccineDao
import com.neochildclinic.data.local.dao.VaccinationDao
import com.neochildclinic.data.local.dao.InventoryDeductionDao
import com.neochildclinic.data.local.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object UseCaseModule {

    @Provides
    @ViewModelScoped
    fun provideGetPatientsUseCase(repository: PatientRepository) = GetPatientsUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideSavePatientUseCase(repository: PatientRepository) = SavePatientUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideDeletePatientUseCase(repository: PatientRepository) = DeletePatientUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideGetVaccinationsUseCase(repository: VaccinationRepository) = GetVaccinationsUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideSaveVaccinationUseCase(repository: VaccinationRepository) = SaveVaccinationUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideDeleteVaccinationUseCase(repository: VaccinationRepository) = DeleteVaccinationUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideRefreshDataUseCase(
        patientRepository: PatientRepository,
        vaccinationRepository: VaccinationRepository,
        wasteRepository: WasteRepository,
        inventoryRepository: InventoryRepository,
        reminderRepository: ReminderRepository,
        consultationRepository: ConsultationRepository
    ) = RefreshDataUseCase(
        patientRepository,
        vaccinationRepository,
        wasteRepository,
        inventoryRepository,
        reminderRepository,
        consultationRepository
    )

    @Provides
    @ViewModelScoped
    fun provideBackfillInventoryUsageUseCase(
        vaccinationRepository: VaccinationRepository,
        inventoryRepository: InventoryRepository,
        vaccineDao: VaccineDao
    ) = BackfillInventoryUsageUseCase(vaccinationRepository, inventoryRepository, vaccineDao)

    @Provides
    @ViewModelScoped
    fun provideReconcileInventoryUseCase(
        vaccinationDao: VaccinationDao,
        vaccineDao: VaccineDao,
        inventoryRepository: InventoryRepository,
        inventoryDeductionDao: InventoryDeductionDao,
        database: AppDatabase
    ) = ReconcileInventoryUseCase(vaccinationDao, vaccineDao, inventoryRepository, inventoryDeductionDao, database)
}
