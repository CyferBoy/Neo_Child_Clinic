package com.neochildclinic.core.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

/**
 * Receives the asynchronous result from Android PackageInstaller.
 * The system installer UI is responsible for the user-facing installation flow.
 */
class AppUpdateInstallReceiver : BroadcastReceiver() {

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
        }
    }
}
