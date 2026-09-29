package com.neochildclinic.core.di

import com.neochildclinic.feature.audit.data.AuditLogRepositoryImpl
import com.neochildclinic.data.backup.BackupRepositoryImpl
import com.neochildclinic.feature.borrowed.data.BorrowRepositoryImpl
import com.neochildclinic.feature.patient.data.ConsultationRepositoryImpl
import com.neochildclinic.feature.auth.data.DeviceRepositoryImpl
import com.neochildclinic.feature.doctor.data.DoctorAvailabilityRepositoryImpl
import com.neochildclinic.feature.patient.data.DocumentRepositoryImpl
import com.neochildclinic.feature.finance.data.ExpenseRepositoryImpl
import com.neochildclinic.feature.finance.data.FinanceRepositoryImpl
import com.neochildclinic.feature.inventory.data.InventoryRepositoryImpl
import com.neochildclinic.feature.patient.data.PatientRepositoryImpl
import com.neochildclinic.feature.patient.data.PatientTodoRepositoryImpl
import com.neochildclinic.feature.personalreminder.data.PersonalReminderRepositoryImpl
import com.neochildclinic.feature.profile.data.ProfileRepositoryImpl
import com.neochildclinic.feature.reminder.data.ReminderRepositoryImpl
import com.neochildclinic.feature.staff.data.StaffManagementRepositoryImpl
import com.neochildclinic.core.sync.SyncRepositoryImpl
import com.neochildclinic.feature.vaccination.data.VaccinationRepositoryImpl
import com.neochildclinic.feature.waste.data.WasteRepositoryImpl
import com.neochildclinic.domain.repository.AuditLogRepository
import com.neochildclinic.domain.repository.BackupRepository
import com.neochildclinic.domain.repository.BorrowRepository
import com.neochildclinic.domain.repository.ConsultationRepository
import com.neochildclinic.domain.repository.DeviceRepository
import com.neochildclinic.domain.repository.DoctorAvailabilityRepository
import com.neochildclinic.domain.repository.DocumentRepository
import com.neochildclinic.domain.repository.ExpenseRepository
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.domain.repository.PatientTodoRepository
import com.neochildclinic.domain.repository.PersonalReminderRepository
import com.neochildclinic.domain.repository.ProfileRepository
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.StaffManagementRepository
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.repository.VaccinationRepository
import com.neochildclinic.domain.repository.WasteRepository
import com.neochildclinic.data.local.database.TransactionRunnerImpl
import com.neochildclinic.domain.TransactionRunner
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
    @Binds abstract fun profileRepository(impl: ProfileRepositoryImpl): ProfileRepository
    @Binds abstract fun staffManagementRepository(impl: StaffManagementRepositoryImpl): StaffManagementRepository
    @Binds abstract fun auditLogRepository(impl: AuditLogRepositoryImpl): AuditLogRepository
    @Binds abstract fun borrowRepository(impl: BorrowRepositoryImpl): BorrowRepository
    @Binds abstract fun deviceRepository(impl: DeviceRepositoryImpl): DeviceRepository
    @Binds abstract fun documentRepository(impl: DocumentRepositoryImpl): DocumentRepository
    @Binds abstract fun backupRepository(impl: BackupRepositoryImpl): BackupRepository
    @Binds abstract fun transactionRunner(impl: TransactionRunnerImpl): TransactionRunner
}