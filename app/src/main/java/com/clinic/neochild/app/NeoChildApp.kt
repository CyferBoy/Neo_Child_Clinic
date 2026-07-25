package com.clinic.neochild.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.clinic.neochild.notification.ReminderScheduler
import com.clinic.neochild.worker.SyncWorker
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.initialize
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class NeoChildApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        // Manually load SQLCipher native library (Required for version 4.6.1+ and 16KB support)
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load sqlcipher library", e)
        }

        super.onCreate()

        // Initialize GMS Security Provider
        installSecurityProvider()

        // Initialize Firebase after super.onCreate()
        setupFirebaseAppCheck()

        setupSync()
        
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        scope.launch {
            reminderScheduler.scheduleDailySummary()
        }
    }

    private fun installSecurityProvider() {
        ProviderInstaller.installIfNeededAsync(this, object : ProviderInstaller.ProviderInstallListener {
            override fun onProviderInstalled() {
                Log.d(TAG, "Security provider installed successfully")
            }

            override fun onProviderInstallFailed(errorCode: Int, recoveryIntent: android.content.Intent?) {
                val availability = GoogleApiAvailability.getInstance()
                if (availability.isUserResolvableError(errorCode)) {
                    Log.w(TAG, "Security provider installation failed: ${availability.getErrorString(errorCode)}. Recovery possible.")
                } else {
                    Log.e(TAG, "Security provider installation failed: ${availability.getErrorString(errorCode)}. Fatal error.")
                }
            }
        })
    }

    private fun setupFirebaseAppCheck() {
        Firebase.initialize(context = this)
        
        // For testing purposes, we use DebugAppCheckProviderFactory even in release.
        // This allows using a debug secret token on any device.
        Log.d(TAG, "Using DebugAppCheckProviderFactory for testing")
        val factory = DebugAppCheckProviderFactory.getInstance()
        
        Firebase.appCheck.installAppCheckProviderFactory(factory)
        Log.d(TAG, "App Check initialized")
    }

    private fun setupSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest,
        )
    }

    companion object {
        private const val TAG = "AppCheck"
        private const val SYNC_WORK_NAME = "SyncWork"
    }
}
