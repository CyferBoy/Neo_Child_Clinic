package com.neochildclinic.di

import com.neochildclinic.data.repository.ConsultationRepositoryImpl
import com.neochildclinic.data.repository.DoctorAvailabilityRepositoryImpl
import com.neochildclinic.data.repository.ExpenseRepositoryImpl
import com.neochildclinic.data.repository.FinanceRepositoryImpl
import com.neochildclinic.data.repository.InventoryRepositoryImpl
import com.neochildclinic.data.repository.PatientRepositoryImpl
import com.neochildclinic.data.repository.PatientTodoRepositoryImpl
import com.neochildclinic.data.repository.PersonalReminderRepositoryImpl
import com.neochildclinic.data.repository.ReminderRepositoryImpl
import com.neochildclinic.data.repository.SyncRepositoryImpl
import com.neochildclinic.data.repository.VaccinationRepositoryImpl
import com.neochildclinic.data.repository.WasteRepositoryImpl
import com.neochildclinic.domain.repository.ConsultationRepository
import com.neochildclinic.domain.repository.DoctorAvailabilityRepository
import com.neochildclinic.domain.repository.ExpenseRepository
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.domain.repository.PatientTodoRepository
import com.neochildclinic.domain.repository.PersonalReminderRepository
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.repository.VaccinationRepository
import com.neochildclinic.domain.repository.WasteRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun patientRepository(impl: PatientRepositoryImpl): PatientRepository
    @Binds abstract fun vaccinationRepository(impl: VaccinationRepositoryImpl): VaccinationRepository
    @Binds abstract fun financeRepository(impl: FinanceRepositoryImpl): FinanceRepository
    @Binds abstract fun reminderRepository(impl: ReminderRepositoryImpl): ReminderRepository
    @Binds abstract fun inventoryRepository(impl: InventoryRepositoryImpl): InventoryRepository
    @Binds abstract fun consultationRepository(impl: ConsultationRepositoryImpl): ConsultationRepository
    @Binds abstract fun syncRepository(impl: SyncRepositoryImpl): SyncRepository
    @Binds abstract fun doctorAvailabilityRepository(impl: DoctorAvailabilityRepositoryImpl): DoctorAvailabilityRepository
    @Binds abstract fun patientTodoRepository(impl: PatientTodoRepositoryImpl): PatientTodoRepository
    @Binds abstract fun personalReminderRepository(impl: PersonalReminderRepositoryImpl): PersonalReminderRepository
    @Binds abstract fun expenseRepository(impl: ExpenseRepositoryImpl): ExpenseRepository
    @Binds abstract fun wasteRepository(impl: WasteRepositoryImpl): WasteRepository
}