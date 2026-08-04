package com.neochildclinic.core.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ReceiptGenerator {

    suspend fun downloadReceipt(context: Context, patient: Patient, vaccination: Vaccination) {
        withContext(Dispatchers.Default) {
            val pdfDocument = PdfDocument()
            try {
                val pageInfo = PdfDocument.PageInfo.Builder(ReceiptFormatter.PAGE_WIDTH, ReceiptFormatter.PAGE_HEIGHT, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                
                ReceiptFormatter.drawReceiptContent(context, page.canvas, patient, vaccination)
                
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
            val outputStream: OutputStream? = withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri: Uri? = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    uri?.let { context.contentResolver.openOutputStream(it) }
                } else {
                    val target = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                    FileOutputStream(target)
                }
            }

            outputStream?.use { 
                pdfDocument.writeTo(it)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, successMessage, Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
