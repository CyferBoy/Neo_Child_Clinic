package com.neochildclinic.feature.patient.presentation

import android.content.ContentValues
import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream

object ReceiptGenerator {

    suspend fun downloadReceipt(context: Context, patient: Patient, vaccination: Vaccination, doctorName: String? = null) {
        withContext(Dispatchers.Default) {
            val pdfDocument = PdfDocument()
            try {
                val pageInfo = PdfDocument.PageInfo.Builder(ReceiptFormatter.PAGE_WIDTH, ReceiptFormatter.PAGE_HEIGHT, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                
                ReceiptFormatter.drawReceiptContent(context, page.canvas, patient, vaccination, doctorName)
                
                pdfDocument.finishPage(page)

                val fileName = "Receipt_${patient.name.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
                writePdfToStorage(context, pdfDocument, fileName, "Receipt downloaded to Downloads folder")
            } finally {
                pdfDocument.close()
            }
        }
    }

    private suspend fun writePdfToStorage(context: Context, pdfDocument: PdfDocument, fileName: String, successMessage: String) {
        try {
            val outputStream: OutputStream = withContext(Dispatchers.IO) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri: Uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IOException("Could not create download entry in MediaStore")
                context.contentResolver.openOutputStream(uri)
                    ?: throw IOException("Could not open output stream for download entry")
            }

            outputStream.use { 
                pdfDocument.writeTo(it)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, successMessage, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
