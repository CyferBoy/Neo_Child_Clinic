package com.neochildclinic.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import com.neochildclinic.features.settings.NotificationSettingsManager
import com.neochildclinic.notification.NotificationHelper
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.postgrest.Postgrest
import com.google.firebase.messaging.FirebaseMessaging
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
    lateinit var messaging: FirebaseMessaging

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var settingsManager: NotificationSettingsManager

    @Inject
    lateinit var syncManager: SyncManager

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
        
        checkAppLock()
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
                        if (isGranted) {
                            fetchAndStoreFcmToken()
                        }
                    }

                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            fetchAndStoreFcmToken()
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
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isAppLocked = false
                }

            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Clinic Access")
            .setSubtitle("Authenticate to access patient data")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun fetchAndStoreFcmToken() {
        val currentUser = auth.currentSessionOrNull()?.user ?: return
        messaging.token.addOnSuccessListener { token ->
            lifecycleScope.launch {
                try {
                    // 1. Update staff table
                    postgrest.from("staff").upsert(
                        mapOf("id" to currentUser.id, "fcm_token" to token)
                    )
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to store FCM token in Supabase", e)
                }
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
