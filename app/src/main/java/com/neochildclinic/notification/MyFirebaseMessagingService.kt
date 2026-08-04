package com.neochildclinic.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Note: We don't have direct access to Auth/Firestore here easily with Hilt in a Service 
        // without some extra boilerplate or using a repository.
        // For now, we rely on the next app open/login to sync the token, 
        // or we can try to inject what we need.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Handle incoming data messages if needed
    }
}
