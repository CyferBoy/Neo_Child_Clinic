package com.neochildclinic.core.utils

import android.content.Context
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination

object ReceiptManager {

    suspend fun downloadReceipt(context: Context, patient: Patient, vaccination: Vaccination, doctorName: String? = null) {
        ReceiptGenerator.downloadReceipt(context, patient, vaccination, doctorName)
    }

    fun printReceipt(context: Context, patient: Patient, vaccination: Vaccination, doctorName: String? = null) {
        ReceiptPrinter.printReceipt(context, patient, vaccination, doctorName)
    }

    fun printConsultationReceipt(context: Context, patient: Patient, consultation: Consultation, doctorName: String? = null) {
        ReceiptPrinter.printConsultationReceipt(context, patient, consultation, doctorName)
    }
}
