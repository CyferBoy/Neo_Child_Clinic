package com.neochildclinic.features.vaccination

import android.content.Context
import android.widget.Toast
import com.neochildclinic.domain.model.Vaccination
import java.text.SimpleDateFormat
import java.util.*

object VaccinationValidator {
    fun validateForm(context: Context, patientId: String, vaccines: List<String>): Boolean {
        if (patientId.isBlank() || vaccines.isEmpty()) {
            Toast.makeText(context, "Patient ID and at least one Vaccine are required", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    fun createVaccination(
        id: String?, patientId: String, vaccines: List<String>, vaccineIds: List<String>, nextVaccine: String, dateGiven: String, nextDue: String,
        cost: String, cash: String, online: String, total: Double, withFees: Boolean, doctorsAcc: Boolean,
        batches: List<String>, batchIds: List<String>, expiries: List<String>, performedBy: String = "", receiptNumber: String? = null
    ): Vaccination {
        val finalId = id ?: UUID.randomUUID().toString()
        val finalReceipt = if (receiptNumber.isNullOrBlank()) {
            // Auto-generate receipt number: VAC-YYYYMMDD-SHORTUUID
            val datePart = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(Date())
            val shortId = finalId.take(4).uppercase()
            "VAC-$datePart-$shortId"
        } else {
            receiptNumber
        }

        val nxtNames = nextVaccine.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val nextVaccinations = if (nextDue.isNotBlank()) {
            listOf(com.neochildclinic.domain.model.NextVaccinationSummary(
                type = "",
                vaccineNames = nxtNames,
                dueDate = nextDue
            ))
        } else emptyList()

        val items = vaccines.mapIndexed { index, name ->
            com.neochildclinic.domain.model.VaccinationItem(
                vaccinationId = finalId,
                vaccineId = vaccineIds.getOrNull(index) ?: "",
                vaccineName = name,
                batchId = batchIds.getOrNull(index) ?: "",
                batchNumber = batches.getOrNull(index) ?: "",
                expiryDate = expiries.getOrNull(index) ?: "",
                quantity = 1,
                mrp = 0.0, // Should be passed in or loaded
                netRate = 0.0
            )
        }

        return Vaccination(
            id = finalId,
            receiptNumber = finalReceipt,
            patientId = patientId,
            dateGiven = dateGiven,
            cashAmount = cash.toDoubleOrNull() ?: 0.0,
            onlineAmount = online.toDoubleOrNull() ?: 0.0,
            totalPaid = total,
            withFees = withFees,
            doctorsAcc = doctorsAcc,
            status = com.neochildclinic.domain.model.ReminderStatus.COMPLETED,
            items = items,
            nextVaccinations = nextVaccinations,
            performedBy = performedBy,
            inventoryStatus = "PENDING"
        )
    }
}
