package com.neochildclinic.feature.update.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.neochildclinic.core.notification.NotificationHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Receives the asynchronous result from Android PackageInstaller.
 * The system installer UI is responsible for the user-facing installation flow -
 * but PackageInstaller can still reject the session afterwards, so a failure here
 * needs to actually reach the user rather than just sitting in Logcat - see
 * showUpdateInstallFailed().
 */
class AppUpdateInstallReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppUpdateInstallReceiverEntryPoint {
        fun notificationHelper(): NotificationHelper
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.neochildclinic.UPDATE_INSTALL_STATUS"
        const val EXTRA_SESSION_ID = "session_id"
        private const val TAG = "AppUpdateInstall"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return

        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppUpdateInstallReceiverEntryPoint::class.java
        )
        val notificationHelper = entryPoint.notificationHelper()

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Android requires user confirmation to proceed with the install.
            // Extract the system's confirmation dialog intent and launch it.
            val confirmIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            if (confirmIntent != null) {
                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirmIntent)
            } else {
                Log.w(TAG, "STATUS_PENDING_USER_ACTION received but Intent.EXTRA_INTENT was null. sessionId=$sessionId")
                notificationHelper.showUpdateInstallFailed("User confirmation prompt could not be displayed.")
            }
            return
        }

        if (status == PackageInstaller.STATUS_SUCCESS) {
            Log.i(TAG, "Update installed successfully. sessionId=$sessionId")
        } else if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
            Log.i(TAG, "Update installation cancelled by user. sessionId=$sessionId")
        } else {
            Log.w(TAG, "Update installation failed. sessionId=$sessionId status=$status message=$message")
            val reason = message?.takeIf { it.isNotBlank() }
                ?: "Android rejected the install (status $status)."
            notificationHelper.showUpdateInstallFailed(reason)
        }
    }
}
