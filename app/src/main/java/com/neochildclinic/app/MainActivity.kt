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
import com.neochildclinic.core.designsystem.NeoChildTheme
import com.neochildclinic.core.ui.LockScreen
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

    private var openDueTab by mutableStateOf(false)
    private var isAppLocked by mutableStateOf(false)

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
                if (isAppLocked) {
                    LockScreen(onAuthenticate = { authenticateWithBiometrics() })
                } else {
                    val navController = rememberNavController()
                    
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

                    AppNavigation(navController = navController)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // No auto-logout anymore. Session remains indefinitely.
        lifecycleScope.launch {
            authViewModel.refreshSessionStatus()
            checkAppLock()
            deviceRepository.updateActivity()
            settingsManager.updateLastOpenTimestamp()
            syncManager.scheduleSync()
        }
    }

    private fun checkAppLock() {
        val currentUser = auth.currentSessionOrNull()?.user
        if (currentUser == null) return

        lifecycleScope.launch {
            val settings = settingsManager.settingsFlow.first()
            if (!settings.biometricLockEnabled) {
                isAppLocked = false
                return@launch
            }

            val currentTime = System.currentTimeMillis()
            val lastOpen = settings.lastAppOpenTimestamp
            val thresholdMillis = settings.inactivityDaysThreshold * 24L * 60L * 60L * 1000L
            
            if (settings.authOnEveryOpen || (currentTime - lastOpen > thresholdMillis)) {
                isAppLocked = true
                authenticateWithBiometrics()
            }
        }
    }

    private fun authenticateWithBiometrics() {
        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        
        when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                val executor = ContextCompat.getMainExecutor(this)
                val biometricPrompt = BiometricPrompt(this, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            isAppLocked = false
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                Toast.makeText(this@MainActivity, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            // Handled by system UI mostly, but we could add custom feedback
                        }
                    })

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Clinic Access")
                    .setSubtitle("Authenticate to access patient data")
                    .setAllowedAuthenticators(authenticators)
                    .build()

                biometricPrompt.authenticate(promptInfo)
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                // If not enrolled but lock is enabled, we might want to fallback to pin or allow entry with warning
                // For now, allow entry to prevent lock-out if they enabled it then deleted fingerprints
                isAppLocked = false
                Toast.makeText(this, "No biometrics enrolled. Please set up fingerprint in device settings.", Toast.LENGTH_LONG).show()
            }
            else -> {
                // Feature not available or hardware error
                isAppLocked = false
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
