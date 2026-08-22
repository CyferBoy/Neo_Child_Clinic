package com.neochildclinic.domain.service

import androidx.room.withTransaction
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.domain.repository.ConsultationRepository
import com.neochildclinic.domain.repository.FinanceRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConsultationEditEngine @Inject constructor(
    private val database: AppDatabase,
    private val consultationRepository: ConsultationRepository,
    private val financeRepository: FinanceRepository,
) {
    enum class Result { UPDATED, NO_CHANGES }

    suspend fun edit(original: Consultation, updated: Consultation, user: String): Result {
        if (original == updated) return Result.NO_CHANGES

        val transactionGroupId = UUID.randomUUID().toString()
        database.withTransaction {
            consultationRepository.updateConsultation(updated, transactionGroupId)

            val financeChanged =
                original.amount != updated.amount ||
                original.cashAmount != updated.cashAmount ||
                original.onlineAmount != updated.onlineAmount

            if (financeChanged) {
                val visitId = if (updated.visitId.isNotBlank()) updated.visitId else original.visitId
                if (visitId.isNotBlank()) {
                    financeRepository.updateConsultationIncome(
                        visitId = visitId,
                        consultationId = updated.id,
                        originalAmount = original.amount,
                        originalCashAmount = original.cashAmount,
                        originalOnlineAmount = original.onlineAmount,
                        amount = updated.amount,
                        cashAmount = updated.cashAmount,
                        onlineAmount = updated.onlineAmount,
                        remarks = "Consultation: ${updated.problem}",
                        recordedBy = user,
                        transactionGroupId = transactionGroupId
                    )
                }
            }
        }
        return Result.UPDATED
    }
}
