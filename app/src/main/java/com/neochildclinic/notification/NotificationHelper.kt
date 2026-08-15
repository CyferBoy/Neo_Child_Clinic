package com.neochildclinic.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.neochildclinic.app.MainActivity
import com.neochildclinic.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_DAILY_SUMMARY = "daily_summary"
        const val CHANNEL_LOW_STOCK = "low_stock_alerts"
        const val CHANNEL_SYNC_BACKUP = "sync_backup_alerts"
        const val CHANNEL_APP_UPDATES = "app_updates"
        
        const val SUMMARY_ID = 1001
        const val LOW_STOCK_ID = 1002
        const val SYNC_ALERT_ID = 1003
        const val APP_UPDATE_ID = 1004
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_DAILY_SUMMARY,
                    "Daily Summary",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Summary of today's work and upcoming vaccinations"
                },
                NotificationChannel(
                    CHANNEL_LOW_STOCK,
                    "Low Stock Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when vaccine stock falls below threshold"
                },
                NotificationChannel(
                    CHANNEL_SYNC_BACKUP,
                    "Sync & Backup Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Critical alerts for data synchronization failures"
                },
                NotificationChannel(
                    CHANNEL_APP_UPDATES,
                    "App Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications about new Vaccine Manager releases"
                }
            )
            notificationManager.createNotificationChannels(channels)
        }
    }

    fun showDailySummary(dueToday: Int, overdue: Int, lowStock: Int) {
        if (dueToday == 0 && overdue == 0 && lowStock == 0) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            SUMMARY_ID, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val content = buildString {
            if (dueToday > 0) append("Today's Due: $dueToday\n")
            if (overdue > 0) append("Overdue: $overdue\n")
            if (lowStock > 0) append("Low Stock: $lowStock\n")
            append("\nTap to open Vaccine Manager.")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_DAILY_SUMMARY)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("🏥 Vaccine Manager Summary")
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        NotificationManagerCompat.from(context).notify(SUMMARY_ID, builder.build())
    }

    fun showLowStockAlert(vaccineName: String, remaining: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            LOW_STOCK_ID, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_LOW_STOCK)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("⚠️ Low Stock Alert")
            .setContentText("$vaccineName: Only $remaining doses left!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        NotificationManagerCompat.from(context).notify(vaccineName.hashCode(), builder.build())
    }

    fun showSyncAlert(error: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_SYNC_BACKUP)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("❌ Sync Failure")
            .setContentText("Firestore sync failed repeatedly: $error")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(SYNC_ALERT_ID, builder.build())
    }

    fun showUpdateNotification(versionName: String, mandatory: Boolean) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CHECK_APP_UPDATE", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            APP_UPDATE_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (mandatory) "Vaccine Manager update required" else "Vaccine Manager update available"
        val text = if (mandatory) {
            "Version $versionName is required. Tap to update."
        } else {
            "Version $versionName is now available. Tap to update."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_APP_UPDATES)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        NotificationManagerCompat.from(context).notify(APP_UPDATE_ID, builder.build())
    }

    fun cancelSummaryNotification() {
        notificationManager.cancel(SUMMARY_ID)
    }

    fun cancelAll() {
        notificationManager.cancelAll()
    }
}
