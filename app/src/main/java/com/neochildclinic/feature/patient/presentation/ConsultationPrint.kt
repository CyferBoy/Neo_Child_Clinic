package com.neochildclinic.feature.patient.presentation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.print.PrintAttributes
import com.neochildclinic.core.common.AgeUtils
import com.neochildclinic.core.common.DateUtils
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import java.util.Locale

/**
 * Two printouts for a consultation history row.
 *
 * Prescription is a transparent A4 overlay: the clinic's pre-printed pad shows through,
 * so only the seven blank fields are filled. Coordinates come from the pad PDF's text
 * boxes (points, top-left origin) measured at 595.4 x 841.8 pt.
 */
object ConsultationPrint {

    private const val PAD_WIDTH = 595
    private const val PAD_HEIGHT = 842

    fun printPrescription(context: Context, patient: Patient, consultation: Consultation) {
        ReceiptPrinter.startPrintJob(
            context = context,
            jobName = "Prescription_${patient.name}_${consultation.date}",
            pageWidth = PAD_WIDTH,
            pageHeight = PAD_HEIGHT,
            printAttributes = a4Attributes()
        ) { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.BLACK
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

            val (age, unit) = AgeUtils.calculateDetailedAge(patient.dob)
            fitted(canvas, patient.name, 52f, 196f, 200f, paint)
            fitted(canvas, "$age $unit", 290f, 196f, 75f, paint)
            fitted(canvas, patient.gender, 398f, 196f, 52f, paint)
            fitted(canvas, patient.uhid, 490f, 196f, 88f, paint)
            fitted(canvas, patient.address.orEmpty(), 62f, 220f, 190f, paint)
            fitted(canvas, patient.phone, 322f, 220f, 108f, paint)
            fitted(canvas, DateUtils.formatDateForDisplay(consultation.date), 466f, 220f, 108f, paint)
        }
    }

    fun printReceipt(
        context: Context,
        patient: Patient,
        consultation: Consultation,
        visit: Vaccination?,
        doctorName: String?
    ) {
        ReceiptPrinter.startPrintJob(context, "Receipt_${patient.name}_${consultation.date}") { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val pageWidth = ReceiptFormatter.PAGE_WIDTH.toFloat()
            var yPos = 40f
            yPos = ReceiptFormatter.drawClinicHeader(context, canvas, paint, yPos)

            paint.textAlign = Paint.Align.RIGHT
            paint.typeface = Typeface.create("serif", Typeface.BOLD)
            paint.textSize = 20f
            paint.color = Color.rgb(200, 200, 200)
            canvas.drawText("RECEIPT", pageWidth - ReceiptFormatter.MARGIN, 55f, paint)
            paint.color = Color.BLACK
            paint.textSize = 9f
            val receiptNo = visit?.let { ReceiptFormatter.formatReceiptNumber(it) }
                ?: "#CN-${consultation.id.takeLast(6).uppercase()}"
            canvas.drawText("No: $receiptNo", pageWidth - ReceiptFormatter.MARGIN, 75f, paint)
            paint.typeface = Typeface.create("serif", Typeface.NORMAL)
            canvas.drawText("Date: ${DateUtils.formatDateForDisplay(consultation.date)}", pageWidth - ReceiptFormatter.MARGIN, 87f, paint)

            paint.textAlign = Paint.Align.LEFT
            paint.strokeWidth = 1.5f
            canvas.drawLine(ReceiptFormatter.MARGIN, yPos, pageWidth - ReceiptFormatter.MARGIN, yPos, paint)
            yPos += 20f

            yPos = ReceiptFormatter.drawPatientInfoBar(canvas, paint, patient, yPos)

            paint.typeface = Typeface.create("serif", Typeface.NORMAL)
            paint.textSize = 9f
            canvas.drawText("Doctor: ${doctorName ?: consultation.doctorName.ifBlank { "Dr. Farogh Hassan" }}", ReceiptFormatter.MARGIN, yPos, paint)
            yPos += 18f
            canvas.drawText("Consultation: ${consultation.problem.ifBlank { "General Consultation" }}", ReceiptFormatter.MARGIN, yPos, paint)
            yPos += 22f

            paint.textAlign = Paint.Align.RIGHT
            paint.typeface = Typeface.create("serif", Typeface.BOLD)
            paint.textSize = 12f
            val total = consultation.amount
            canvas.drawText(
                "Total Paid: ₹${String.format(Locale.getDefault(), "%.2f", total)}",
                pageWidth - ReceiptFormatter.MARGIN, yPos, paint
            )
            yPos += 16f
            paint.textSize = 9f
            paint.typeface = Typeface.create("serif", Typeface.NORMAL)
            val mode = when {
                consultation.cashAmount > 0 && consultation.onlineAmount > 0 -> "Cash & UPI"
                consultation.cashAmount > 0 -> "Cash"
                consultation.onlineAmount > 0 -> "UPI"
                else -> "Not Specified"
            }
            canvas.drawText("Mode: $mode", pageWidth - ReceiptFormatter.MARGIN, yPos, paint)
        }
    }

    private fun a4Attributes(): PrintAttributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .setResolution(PrintAttributes.Resolution("print", "print", 300, 300))
        .build()

    /** Clip to the field width with an ellipsis so one value cannot spill into its neighbour. */
    private fun fitted(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float, paint: Paint) {
        val value = text.trim()
        if (value.isEmpty()) return
        val count = paint.breakText(value, true, maxWidth, null)
        val drawn = if (count < value.length) value.take((count - 1).coerceAtLeast(0)) + "…" else value
        canvas.drawText(drawn, x, y, paint)
    }
}
