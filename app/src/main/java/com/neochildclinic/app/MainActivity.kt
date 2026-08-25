package com.neochildclinic.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.neochildclinic.core.utils.BiometricLockManager
import com.neochildclinic.core.utils.BiometricAuthenticator
import com.neochildclinic.core.designsystem.NeoChildTheme
import com.neochildclinic.core.ui.LockScreen
import com.neochildclinic.core.ui.AppUpdateDialog
import com.neochildclinic.features.settings.AppUpdateViewModel
import com.neochildclinic.domain.manager.SyncManager
import com.neochildclinic.domain.repository.DeviceRepository
import com.neochildclinic.features.dashboard.AuthViewModel
import com.neochildclinic.features.settings.NotificationSettingsManager
import com.neochildclinic.notification.NotificationHelper
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.postgrest.Postgrest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    
    @Inject
    lateinit var supabaseClient: io.github.jan.supabase.SupabaseClient

    @Inject
    lateinit var auth: Auth
    
    @Inject
    lateinit var postgrest: Postgrest
    
    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var settingsManager: NotificationSettingsManager

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var deviceRepository: DeviceRepository

    private val authViewModel: AuthViewModel by viewModels()
    private val appUpdateViewModel: AppUpdateViewModel by viewModels()

    private var openDueTab by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supabaseClient.handleDeeplinks(intent)
        logSigningFingerprint()
        handleIntent(intent)
        
        notificationHelper.cancelSummaryNotification()

        // SECURITY: Prevent screenshots and recording of patient data
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        
        syncManager.scheduleSync()

        enableEdgeToEdge()
        setContent {
            NeoChildTheme {
                val isLocked by BiometricLockManager.isAppLocked.collectAsState()
                val navController = rememberNavController()
                val updateInfo by appUpdateViewModel.updateInfo.collectAsState()
                val installingUpdate by appUpdateViewModel.installing.collectAsState()
                val downloadProgress by appUpdateViewModel.downloadProgress.collectAsState()
                
                Box(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(navController = navController, appUpdateViewModel = appUpdateViewModel)
                    
                    if (isLocked) {
                        LockScreen(onAuthenticate = { authenticateWithBiometrics() }, onPasswordAuthenticate = { authenticateWithAccountPassword(it) })
                    }

                    updateInfo?.let { info ->
                        AppUpdateDialog(
                            info = info,
                            installing = installingUpdate,
                            progress = downloadProgress,
                            onUpdate = { appUpdateViewModel.installUpdate() },
                            onLater = { appUpdateViewModel.dismissUpdate() }
                        )
                    }
                }

                // Permission request for Android 13+
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    // Notifications permission granted/denied
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                LaunchedEffect(openDueTab) {
                    if (openDueTab) {
                        navController.navigate(Routes.DUE)
                        openDueTab = false
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Notify Lock Manager that app is resumed
        BiometricLockManager.onAppResume()
        
        lifecycleScope.launch {
            authViewModel.refreshSessionStatus()
            checkAppLock()
            deviceRepository.updateActivity()
            settingsManager.updateLastOpenTimestamp()
            syncManager.scheduleSync()
            if (auth.currentSessionOrNull() != null) {
                appUpdateViewModel.checkForUpdates()
            }
        }
    }

    private fun checkAppLock() {
        val currentUser = auth.currentSessionOrNull()?.user
        if (currentUser == null) return

        lifecycleScope.launch {
            val settings = settingsManager.settingsFlow.first()
            if (!settings.biometricLockEnabled) {
                BiometricLockManager.unlockBecauseProtectionIsDisabled()
                return@launch
            }

            val currentTime = System.currentTimeMillis()
            val lastOpen = settings.lastAppOpenTimestamp
            val thresholdMillis = settings.inactivityDaysThreshold * 24L * 60L * 60L * 1000L
            
            val isLocked = BiometricLockManager.isAppLocked.value
            if (isLocked || settings.authOnEveryOpen || (currentTime - lastOpen > thresholdMillis)) {
                BiometricLockManager.lock()
                authenticateWithBiometrics()
            }
        }
    }

    private fun authenticateWithBiometrics() {
        BiometricAuthenticator.authenticate(
            activity = this,
            title = "Clinic Access",
            subtitle = "Authenticate to access patient data"
        ) {
            BiometricLockManager.unlockAfterKeystoreVerification()
        }
    }

    private fun authenticateWithAccountPassword(password: String) {
        val email = auth.currentSessionOrNull()?.user?.email
        if (email.isNullOrBlank()) {
            Toast.makeText(this, "No account email is available.", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                auth.signInWith(io.github.jan.supabase.auth.providers.builtin.Email) {
                    this.email = email
                    this.password = password
                }
                BiometricLockManager.unlockAfterKeystoreVerification()
            } catch (e: Exception) {
                Log.e("ACCOUNT_AUTH", "Account password authentication failed", e)
                BiometricLockManager.lock()
                Toast.makeText(this@MainActivity, "Incorrect account password.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        supabaseClient.handleDeeplinks(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // Glance action parameters are passed as extras with keys prefixed or mapped
        // For actionStartActivity<MainActivity>(actionParametersOf(key to value))
        // The key is usually the string name used in ActionParameters.Key
        if (intent?.extras?.containsKey("OPEN_DUE_TAB") == true) {
            openDueTab = intent.getBooleanExtra("OPEN_DUE_TAB", false)
        }
        if (intent?.getBooleanExtra("CHECK_APP_UPDATE", false) == true) {
            if (auth.currentSessionOrNull() != null) {
                appUpdateViewModel.checkForUpdates()
            }
        }
    }

    private fun logSigningFingerprint() {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            signatures?.forEach { signature ->
                val md = MessageDigest.getInstance("SHA-1")
                md.update(signature.toByteArray())
                val sha1 = md.digest().joinToString(":") { "%02X".format(it) }
                Log.d("DIAGNOSTIC", "App SHA-1: $sha1")
            }
        } catch (e: Exception) {
            Log.e("DIAGNOSTIC", "Failed to log SHA-1", e)
        }
    }
}
