package com.neochildclinic.features.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.neochildclinic.notification.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives the asynchronous result from Android PackageInstaller.
 * The system installer UI is responsible for the user-facing installation flow -
 * but PackageInstaller can still reject the session afterwards (most notably for
 * a DOWNGRADE install, which a non-privileged app can essentially never get
 * accepted), so a failure here needs to actually reach the user rather than
 * just sitting in Logcat - see showUpdateInstallFailed().
 */
@AndroidEntryPoint
class AppUpdateInstallReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

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

        if (status == PackageInstaller.STATUS_SUCCESS) {
            Log.i(TAG, "Update installed successfully. sessionId=$sessionId")
        } else {
            Log.w(TAG, "Update installation failed. sessionId=$sessionId status=$status message=$message")
            val reason = message?.takeIf { it.isNotBlank() }
                ?: "Android rejected the install (status $status). If this was an older " +
                    "version than what's currently installed, most devices block that " +
                    "without special system privileges."
            notificationHelper.showUpdateInstallFailed(reason)
        }
    }
}
