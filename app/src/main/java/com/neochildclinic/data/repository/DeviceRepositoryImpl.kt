package com.neochildclinic.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.neochildclinic.domain.model.UserDevice
import com.neochildclinic.domain.repository.DeviceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: Auth,
    private val postgrest: Postgrest
) : DeviceRepository {

    private val userDevicesTable = postgrest.from("user_devices")

    override suspend fun registerCurrentDevice() {
        val token = getFcmToken()
        if (token == null) {
            Log.w(TAG, "FCM token unavailable; device registration skipped")
            return
        }
        registerDeviceWithToken(token)
    }

    override suspend fun registerDeviceWithToken(token: String) {
        val currentUser = auth.currentSessionOrNull()?.user
        if (currentUser == null) {
            Log.w(TAG, "No authenticated Supabase user; device registration skipped")
            return
        }

        val now = Instant.now().toString()
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val appVersion = getAppVersion()

        try {
            // Reuse an existing row for this token. This keeps registration idempotent.
            val existingDevice = userDevicesTable.select {
                filter {
                    eq("fcm_token", token)
                    eq("user_id", currentUser.id)
                }
            }.decodeSingleOrNull<UserDevice>()

            val device = UserDevice(
                id = existingDevice?.id,
                userId = currentUser.id,
                fcmToken = token,
                deviceName = deviceName,
                appVersion = appVersion,
                isActive = true,
                lastSeenAt = now,
                createdAt = existingDevice?.createdAt ?: now
            )

            userDevicesTable.upsert(device)

            Log.d(TAG, "FCM device registered successfully for user ${currentUser.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register FCM device", e)
            throw e
        }
    }

    override suspend fun deactivateCurrentDevice() {
        val token = getFcmToken() ?: return

        try {
            userDevicesTable.update(
                mapOf(
                    "is_active" to false,
                    "last_seen_at" to Instant.now().toString()
                )
            ) {
                filter {
                    eq("fcm_token", token)
                    eq("user_id", auth.currentSessionOrNull()?.user?.id ?: "")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deactivate FCM device", e)
        }
    }

    override suspend fun updateActivity() {
        val currentUser = auth.currentSessionOrNull()?.user ?: return
        val token = getFcmToken() ?: return

        try {
            userDevicesTable.update(
                mapOf(
                    "last_seen_at" to Instant.now().toString(),
                    "app_version" to getAppVersion(),
                    "is_active" to true
                )
            ) {
                filter {
                    eq("fcm_token", token)
                    eq("user_id", currentUser.id)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update FCM device activity", e)
        }
    }

    private suspend fun getFcmToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to obtain FCM token", e)
            null
        }
    }

    private fun getAppVersion(): String {
        return try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    companion object {
        private const val TAG = "DeviceRepository"
    }
}
