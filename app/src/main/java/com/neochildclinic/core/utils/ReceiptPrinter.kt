package com.neochildclinic.core.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import java.io.FileOutputStream

object ReceiptPrinter {

    fun printReceipt(context: Context, patient: Patient, vaccination: Vaccination, doctorName: String? = null) {
        val jobName = "Receipt_${patient.name}_${vaccination.dateGiven}"
        startPrintJob(context, jobName) { canvas ->
            ReceiptFormatter.drawReceiptContent(context, canvas, patient, vaccination, doctorName)
        }
    }

    fun printConsultationReceipt(context: Context, patient: Patient, consultation: Consultation, doctorName: String? = null) {
        val jobName = "Consultation_${patient.name}_${consultation.date}"
        startPrintJob(context, jobName) { canvas ->
            ReceiptFormatter.drawConsultationContent(context, canvas, patient, consultation, doctorName)
        }
    }

    private fun startPrintJob(context: Context, jobName: String, drawContent: (Canvas) -> Unit) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }

                val pdi = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()

                callback.onLayoutFinished(pdi, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(ReceiptFormatter.PAGE_WIDTH, ReceiptFormatter.PAGE_HEIGHT, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                
                drawContent(page.canvas)
                
                pdfDocument.finishPage(page)

                try {
                    pdfDocument.writeTo(FileOutputStream(destination?.fileDescriptor))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                    return
                } finally {
                    pdfDocument.close()
                }

                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        }, null)
    }
}
