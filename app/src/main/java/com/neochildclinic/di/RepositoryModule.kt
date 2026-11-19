package com.neochildclinic.di

import com.neochildclinic.app.work.ReminderSchedulerImpl
import com.neochildclinic.app.work.SyncManagerImpl
import com.neochildclinic.core.database.TransactionRunner
import com.neochildclinic.core.logger.AuditLogWriter
import com.neochildclinic.core.notification.ReminderScheduler
import com.neochildclinic.core.security.UserDisplayNameProvider
import com.neochildclinic.core.sync.SyncEnqueuer
import com.neochildclinic.core.sync.SyncScheduler
import com.neochildclinic.data.backup.BackupRepositoryImpl
import com.neochildclinic.data.local.database.TransactionRunnerImpl
import com.neochildclinic.feature.audit.data.AuditLogRepositoryImpl
import com.neochildclinic.domain.repository.AuditLogRepository
import com.neochildclinic.feature.auth.data.repository.AuthRepositoryImpl
import com.neochildclinic.feature.auth.domain.repository.AuthRepository
import com.neochildclinic.feature.auth.data.DeviceRepositoryImpl
import com.neochildclinic.domain.repository.DeviceRepository
import com.neochildclinic.feature.borrowed.data.BorrowRepositoryImpl
import com.neochildclinic.domain.repository.BorrowRepository
import com.neochildclinic.feature.consultation.data.ConsultationRepositoryImpl
import com.neochildclinic.domain.repository.ConsultationRepository
import com.neochildclinic.feature.doctor.data.DoctorAvailabilityRepositoryImpl
import com.neochildclinic.domain.repository.DoctorAvailabilityRepository
import com.neochildclinic.feature.finance.data.ExpenseRepositoryImpl
import com.neochildclinic.feature.finance.data.FinanceRepositoryImpl
import com.neochildclinic.domain.repository.ExpenseRepository
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.feature.inventory.data.InventoryRepositoryImpl
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.feature.patient.data.DocumentRepositoryImpl
import com.neochildclinic.feature.patient.data.PatientRepositoryImpl
import com.neochildclinic.feature.patient.data.PatientTodoRepositoryImpl
import com.neochildclinic.domain.repository.DocumentRepository
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.domain.repository.PatientTodoRepository
import com.neochildclinic.feature.personalreminder.data.PersonalReminderRepositoryImpl
import com.neochildclinic.domain.repository.PersonalReminderRepository
import com.neochildclinic.feature.profile.data.ProfileRepositoryImpl
import com.neochildclinic.domain.repository.ProfileRepository
import com.neochildclinic.feature.reminder.data.ReminderRepositoryImpl
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.BackupRepository
import com.neochildclinic.domain.repository.AppUpdateManager
import com.neochildclinic.domain.repository.BackupAutoScheduler
import com.neochildclinic.feature.staff.data.StaffManagementRepositoryImpl
import com.neochildclinic.feature.staff.domain.repository.StaffManagementRepository
import com.neochildclinic.feature.sync.data.SyncRepositoryImpl
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.feature.vaccination.data.VaccinationRepositoryImpl
import com.neochildclinic.domain.repository.VaccinationRepository
import com.neochildclinic.feature.waste.data.WasteRepositoryImpl
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
    @Binds abstract fun profileRepository(impl: ProfileRepositoryImpl): ProfileRepository
    @Binds abstract fun staffManagementRepository(impl: StaffManagementRepositoryImpl): StaffManagementRepository
    @Binds abstract fun auditLogRepository(impl: AuditLogRepositoryImpl): AuditLogRepository
    @Binds abstract fun auditLogWriter(impl: AuditLogRepositoryImpl): AuditLogWriter
    @Binds abstract fun borrowRepository(impl: BorrowRepositoryImpl): BorrowRepository
    @Binds abstract fun deviceRepository(impl: DeviceRepositoryImpl): DeviceRepository
    @Binds abstract fun authRepository(impl: AuthRepositoryImpl): AuthRepository
    @Binds abstract fun documentRepository(impl: DocumentRepositoryImpl): DocumentRepository
    @Binds abstract fun backupRepository(impl: BackupRepositoryImpl): BackupRepository
    @Binds abstract fun appUpdateManager(
        impl: com.neochildclinic.feature.update.data.AppUpdateManager
    ): AppUpdateManager<com.neochildclinic.feature.update.data.AppUpdateInfo>
    @Binds abstract fun backupAutoScheduler(
        impl: com.neochildclinic.data.backup.BackupAutoScheduler
    ): BackupAutoScheduler
    @Binds abstract fun transactionRunner(impl: TransactionRunnerImpl): TransactionRunner

    @Binds abstract fun syncEnqueuer(impl: SyncRepositoryImpl): SyncEnqueuer
    @Binds abstract fun userDisplayNameProvider(impl: ProfileRepositoryImpl): UserDisplayNameProvider
    @Binds abstract fun syncScheduler(impl: SyncManagerImpl): SyncScheduler
    @Binds abstract fun reminderScheduler(impl: ReminderSchedulerImpl): ReminderScheduler
}
