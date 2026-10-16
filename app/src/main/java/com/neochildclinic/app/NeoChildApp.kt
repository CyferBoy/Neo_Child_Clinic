package com.neochildclinic.app

import android.app.Application
import android.util.Log
import android.webkit.WebView
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.neochildclinic.core.notification.ReminderScheduler
import com.neochildclinic.feature.finance.data.FinanceRepositoryImpl
import com.neochildclinic.feature.vaccination.data.VaccinationRepositoryImpl
import com.neochildclinic.core.sync.SyncWorker
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.security.ProviderInstaller
import android.content.Intent
import android.content.IntentFilter
import com.neochildclinic.core.security.BiometricLockManager
import com.neochildclinic.core.preferences.NotificationSettingsManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class NeoChildApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // dagger.Lazy, not lateinit: field injection constructs these during onCreate() on the
    // main thread, and each of them pulls AppDatabase.getDatabase() -> Keystore +
    // EncryptedSharedPreferences + a full SQLCipher file open. On a cold start that froze
    // the UI for hundreds of ms (seconds when the Keystore is slow, e.g. right after a
    // reboot). Resolved inside the IO scope below instead, where it always runs anyway.
    @Inject
    lateinit var reminderScheduler: dagger.Lazy<ReminderScheduler>

    @Inject
    lateinit var financeRepository: dagger.Lazy<FinanceRepositoryImpl>

    @Inject
    lateinit var vaccinationRepository: dagger.Lazy<VaccinationRepositoryImpl>

    @Inject
    lateinit var backupAutoScheduler: dagger.Lazy<com.neochildclinic.data.backup.BackupAutoScheduler>

    @Inject
    lateinit var syncReconciler: dagger.Lazy<com.neochildclinic.core.sync.SyncReconciler>

    @Inject
    lateinit var settingsManager: dagger.Lazy<NotificationSettingsManager>

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

        // Enable WebView debugging for debug builds
        if (com.neochildclinic.BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // Initialize GMS Security Provider
        installSecurityProvider()

        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

        scope.launch {
            setupSync()
            // Startup + reconnection reconciliation: pull missed remote changes and push
            // pending local ones as soon as we're online and signed in (see SyncReconciler).
            // Off the main thread: building the reconciler opens the database.
            syncReconciler.get().start()
        }

        // BiometricLockManager's lock state is in-memory and dies with the process;
        // mirror the arm flag into DataStore (NotificationSettingsManager.needsAuth) so a
        // screen-off + process-death cycle still locks on the next cold start
        // (MainActivity.checkAppLock reads the same flag).
        BiometricLockManager.onNeedsAuthChanged = { needsAuth ->
            scope.launch { settingsManager.get().setNeedsAuth(needsAuth) }
        }
        // Re-arm before any UI renders: checkAppLock has to wait for the session status to
        // resolve (seconds when offline), and patient data must not be visible meanwhile.
        scope.launch {
            val settings = settingsManager.get().settingsFlow.first()
            if (settings.biometricLockEnabled && settings.needsAuth) BiometricLockManager.lock()
        }

        // Register Screen Off Receiver
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    BiometricLockManager.onScreenOff()
                }
            }
        }, filter)

        scope.launch {
            reminderScheduler.get().scheduleDailySummary()
            reminderScheduler.get().schedulePersonalReminderNotifications()
        }
        scope.launch {
            try {
                val vaccinations = vaccinationRepository.get().allVaccinations.first()
                financeRepository.get().migrateLegacyVaccinationCogs(vaccinations)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to migrate historical finance COGS snapshots", e)
            }
        }
        scope.launch {
            try {
                backupAutoScheduler.get().rearmIfEnabled()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to re-arm Automatic Backup scheduling", e)
            }
        }
    }

    private fun installSecurityProvider() {
        val availability = GoogleApiAvailability.getInstance()
        val result = availability.isGooglePlayServicesAvailable(applicationContext)
        if (result != com.google.android.gms.common.ConnectionResult.SUCCESS) {
            Log.w(TAG, "Google Play Services not available: ${availability.getErrorString(result)}")
            return
        }

        ProviderInstaller.installIfNeededAsync(applicationContext, object : ProviderInstaller.ProviderInstallListener {
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
