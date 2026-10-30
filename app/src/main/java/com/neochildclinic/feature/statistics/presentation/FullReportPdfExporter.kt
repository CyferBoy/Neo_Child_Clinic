package com.neochildclinic.feature.statistics.presentation

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.widget.Toast
import com.neochildclinic.core.common.CurrencyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FullReportPdfExporter {
    const val PAGE_WIDTH = 595 // Standard A4 width in points
    const val PAGE_HEIGHT = 842 // Standard A4 height in points
    const val MARGIN = 40f

    fun getFileName(periodLabel: String): String {
        val safePeriod = periodLabel.trim()
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "report" }
        return "${safePeriod}_full_report.pdf"
    }

    suspend fun exportPdf(context: Context, periodLabel: String, summary: FullReportSummary) {
        withContext(Dispatchers.Default) {
            val pdfDocument = PdfDocument()
            try {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                drawReportContent(context, page.canvas, periodLabel, summary)
                pdfDocument.finishPage(page)

                val fileName = getFileName(periodLabel)
                writePdfToStorage(context, pdfDocument, fileName)
            } finally {
                pdfDocument.close()
            }
        }
    }

    fun printReport(context: Context, periodLabel: String, summary: FullReportSummary) {
        val jobName = "Report_${periodLabel.replace(" ", "_")}"
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
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
                if (destination == null) return
                val pdfDocument = PdfDocument()
                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    drawReportContent(context, page.canvas, periodLabel, summary)
                    pdfDocument.finishPage(page)

                    FileOutputStream(destination.fileDescriptor).use { out ->
                        pdfDocument.writeTo(out)
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                } finally {
                    pdfDocument.close()
                }
            }
        }, null)
    }

    private suspend fun writePdfToStorage(context: Context, pdfDocument: PdfDocument, fileName: String) {
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

            outputStream.use { out ->
                pdfDocument.writeTo(out)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to save PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun drawReportContent(context: Context, canvas: Canvas, periodLabel: String, summary: FullReportSummary) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var y = MARGIN

        // Clinic Header
        paint.color = Color.rgb(22, 85, 143) // Clinic Blue #16558F
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        canvas.drawText("NEO CHILD CLINIC", MARGIN, y + 20f, paint)

        paint.color = Color.rgb(95, 109, 104) // Ink Muted #5F6D68
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Generated: $dateStr", PAGE_WIDTH - MARGIN, y + 20f, paint)
        paint.textAlign = Paint.Align.LEFT
        y += 44f

        // Document Title
        paint.color = Color.rgb(20, 32, 31) // Ink Primary #14201F
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 17f
        canvas.drawText("Full Clinic Report", MARGIN, y + 16f, paint)

        paint.color = Color.rgb(95, 109, 104)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 11f
        canvas.drawText("$periodLabel · clinic-wide", MARGIN, y + 34f, paint)
        y += 50f

        // Hero KPI Box
        val bannerHeight = 65f
        val bannerRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + bannerHeight)
        paint.color = Color.rgb(220, 233, 248) // #DCE9F8
        canvas.drawRoundRect(bannerRect, 12f, 12f, paint)

        // Inside KPI Box
        paint.color = Color.rgb(10, 47, 82)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        canvas.drawText("Net Profit", MARGIN + 16f, y + 22f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 22f
        canvas.drawText(CurrencyUtils.formatInr(summary.netProfit), MARGIN + 16f, y + 48f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10.5f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Revenue: ${CurrencyUtils.formatInr(summary.totalRevenue)}", PAGE_WIDTH - MARGIN - 16f, y + 26f, paint)
        canvas.drawText("Expenses: ${CurrencyUtils.formatInr(summary.totalExpenses)}", PAGE_WIDTH - MARGIN - 16f, y + 46f, paint)
        paint.textAlign = Paint.Align.LEFT
        y += bannerHeight + 25f

        // Section: Summary
        paint.color = Color.rgb(20, 32, 31)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 14f
        canvas.drawText("Summary", MARGIN, y, paint)
        y += 12f

        val tableRows = listOf(
            Triple("Total patients", "${summary.totalPatients}", "+${summary.newPatientsInPeriod}"),
            Triple("Consultations", "${summary.consultationCount}", CurrencyUtils.formatInr(summary.consultationRevenue)),
            Triple("Vaccinations", "${summary.vaccinationCount}", CurrencyUtils.formatInr(summary.vaccinationRevenue)),
            Triple("Vaccine COGS", if (!summary.isCogsComplete) "—" else "${summary.vaccinationCount}", if (!summary.isCogsComplete) "Pending" else CurrencyUtils.formatInr(summary.cogsAmount)),
            Triple("Expenses", "${summary.expenseItemCount} items", CurrencyUtils.formatInr(summary.totalExpenses)),
            Triple("Net profit", "", CurrencyUtils.formatInr(summary.netProfit))
        )

        val tableRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + (tableRows.size * 30f) + 8f)
        paint.color = Color.WHITE
        canvas.drawRoundRect(tableRect, 10f, 10f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.rgb(216, 210, 198) // #D8D2C6
        canvas.drawRoundRect(tableRect, 10f, 10f, paint)
        paint.style = Paint.Style.FILL

        var rowY = y + 10f
        tableRows.forEachIndexed { index, (label, col2, col3) ->
            val isNetProfit = index == tableRows.size - 1
            paint.color = if (isNetProfit) Color.rgb(20, 32, 31) else Color.rgb(95, 109, 104)
            paint.typeface = Typeface.create(Typeface.DEFAULT, if (isNetProfit) Typeface.BOLD else Typeface.NORMAL)
            paint.textSize = 10.5f
            canvas.drawText(label, MARGIN + 14f, rowY + 16f, paint)

            paint.textAlign = Paint.Align.RIGHT
            paint.color = Color.rgb(95, 109, 104)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(col2, PAGE_WIDTH / 2f + 40f, rowY + 16f, paint)

            paint.color = if (isNetProfit) Color.rgb(6, 118, 71) else Color.rgb(20, 32, 31)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 11f
            canvas.drawText(col3, PAGE_WIDTH - MARGIN - 14f, rowY + 16f, paint)
            paint.textAlign = Paint.Align.LEFT

            if (index < tableRows.size - 1) {
                paint.color = Color.rgb(216, 210, 198)
                paint.strokeWidth = 1f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(MARGIN + 14f, rowY + 26f, PAGE_WIDTH - MARGIN - 14f, rowY + 26f, paint)
                paint.style = Paint.Style.FILL
            }
            rowY += 30f
        }
        y = rowY + 14f

        if (!summary.isCogsComplete && summary.pendingCogsCount > 0) {
            paint.color = Color.rgb(138, 83, 0)
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("COGS pending for ${summary.pendingCogsCount} batches · net profit is provisional", MARGIN, y, paint)
            y += 20f
        }

        y += 12f

        // Payment Methods Section
        paint.color = Color.rgb(20, 32, 31)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 14f
        canvas.drawText("Payment methods", MARGIN, y, paint)
        y += 12f

        val totalInc = summary.cashTotal + summary.onlineTotal
        val cashPct = if (totalInc > 0) ((summary.cashTotal / totalInc) * 100).toInt() else 0
        val onlinePct = if (totalInc > 0) 100 - cashPct else 0

        val payRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 64f)
        paint.color = Color.WHITE
        canvas.drawRoundRect(payRect, 10f, 10f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.rgb(216, 210, 198)
        canvas.drawRoundRect(payRect, 10f, 10f, paint)
        paint.style = Paint.Style.FILL

        // Cash column
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.rgb(20, 32, 31)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 16f
        val leftCenterX = MARGIN + (PAGE_WIDTH - 2 * MARGIN) * 0.25f
        canvas.drawText(CurrencyUtils.formatInr(summary.cashTotal), leftCenterX, y + 26f, paint)

        paint.color = Color.rgb(95, 109, 104)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9.5f
        canvas.drawText("cash · $cashPct%", leftCenterX, y + 44f, paint)

        // Divider
        paint.color = Color.rgb(216, 210, 198)
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        val midX = PAGE_WIDTH / 2f
        canvas.drawLine(midX, y + 12f, midX, y + 52f, paint)
        paint.style = Paint.Style.FILL

        // Online column
        val rightCenterX = MARGIN + (PAGE_WIDTH - 2 * MARGIN) * 0.75f
        paint.color = Color.rgb(20, 32, 31)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 16f
        canvas.drawText(CurrencyUtils.formatInr(summary.onlineTotal), rightCenterX, y + 26f, paint)

        paint.color = Color.rgb(95, 109, 104)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9.5f
        canvas.drawText("online · $onlinePct%", rightCenterX, y + 44f, paint)
        paint.textAlign = Paint.Align.LEFT

        // Footer
        paint.color = Color.rgb(95, 109, 104)
        paint.textSize = 8.5f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Neo Child Clinic · Confidential Report", PAGE_WIDTH / 2f, PAGE_HEIGHT - MARGIN, paint)
        paint.textAlign = Paint.Align.LEFT
    }
}
