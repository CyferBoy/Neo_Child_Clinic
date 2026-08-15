package com.neochildclinic.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.neochildclinic.domain.repository.DeviceRepository
import android.content.Intent
import com.neochildclinic.app.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NeoChildFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var deviceRepository: DeviceRepository

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onMessageReceived(message: com.google.firebase.messaging.RemoteMessage) {
        super.onMessageReceived(message)
        if (message.data["type"] == "app_update") {
            notificationHelper.showUpdateNotification(
                versionName = message.data["version_name"] ?: "new version",
                mandatory = message.data["mandatory"] == "true"
            )
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // Firebase can rotate the token while the app is installed.
        // The repository associates the new token with the currently
        // authenticated Supabase user when a session is available.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                deviceRepository.registerDeviceWithToken(token)
            }
        }
    }
}
