package com.neochildclinic.feature.consultation.domain.usecase

import com.neochildclinic.core.common.DateUtils
import com.neochildclinic.core.database.TransactionRunner
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.domain.model.Visit
import com.neochildclinic.feature.consultation.domain.repository.ConsultationRepository
import com.neochildclinic.feature.finance.domain.repository.FinanceRepository
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import com.neochildclinic.feature.vaccination.domain.repository.VaccinationRepository
import java.util.UUID
import javax.inject.Inject

/**
 * UseCase to coordinate the atomic creation of a consultation visit.
 *
 * Encapsulates:
 * 1. Creation and persistence of the parent Visit header in patient_visits
 * 2. Outbox sync enqueueing for the VISIT entity
 * 3. Consultation record persistence and sync enqueueing
 * 4. Finance ledger transaction recording
 */
class RecordConsultationUseCase @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val vaccinationRepository: VaccinationRepository,
    private val consultationRepository: ConsultationRepository,
    private val financeRepository: FinanceRepository,
    private val syncRepository: SyncRepository
) {
    suspend operator fun invoke(
        consultation: Consultation,
        user: String
    ) {
        val transactionGroupId = UUID.randomUUID().toString()
        transactionRunner.run {
            val visitId = if (consultation.visitId.isBlank()) UUID.randomUUID().toString() else consultation.visitId

            val consultationTimestamp = DateUtils.getCurrentIsoTimestamp()
            val visit = Visit(
                id = visitId,
                patientId = consultation.patientId,
                dateGiven = consultation.date,
                doctorId = consultation.doctorId,
                doctor = consultation.doctorName,
                visitType = "CONSULTATION",
                cashAmount = consultation.cashAmount,
                onlineAmount = consultation.onlineAmount,
                totalPaid = consultation.amount,
                createdAt = consultationTimestamp,
                updatedAt = consultationTimestamp
            )
            vaccinationRepository.insertVisit(visit)

            syncRepository.enqueue(
                entityName = "VISIT",
                entityId = visitId,
                operation = SyncOperation.CREATE,
                priority = SyncPriority.HIGH,
                transactionGroupId = transactionGroupId
            )

            val finalConsultation = consultation.copy(visitId = visitId)
            consultationRepository.addConsultation(finalConsultation, transactionGroupId)

            financeRepository.recordIncome(
                amount = consultation.amount,
                cashAmount = consultation.cashAmount,
                onlineAmount = consultation.onlineAmount,
                category = "CONSULTATION",
                patientId = consultation.patientId,
                visitId = visitId,
                remarks = "Consultation: ${consultation.problem} [CONSULTATION_ID:${finalConsultation.id}]",
                recordedBy = user,
                transactionGroupId = transactionGroupId
            )
        }
    }
}
