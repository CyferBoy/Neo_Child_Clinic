package com.neochildclinic.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.neochildclinic.domain.model.UserDevice
import com.neochildclinic.domain.repository.DeviceRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

@Singleton
class DeviceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: Auth,
    private val postgrest: Postgrest
) : DeviceRepository {

    private val userDevicesTable = postgrest.from("user_devices")

    override suspend fun registerCurrentDevice() {
        val token = getFcmToken() ?: return
        registerDeviceWithToken(token)
    }

    override suspend fun registerDeviceWithToken(token: String) {
        val currentUser = auth.currentSessionOrNull()?.user ?: return
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        val appVersion = getAppVersion()

        try {
            // Check if device already registered with this token
            val existingDevice = userDevicesTable.select {
                filter {
                    eq("fcm_token", token)
                }
            }.decodeSingleOrNull<UserDevice>()

            if (existingDevice != null) {
                userDevicesTable.update(
                    mapOf(
                        "user_id" to currentUser.id,
                        "device_name" to deviceName,
                        "platform" to "Android",
                        "app_version" to appVersion,
                        "is_active" to true,
                        "last_seen_at" to java.time.Instant.now().toString()
                    )
                ) {
                    filter { eq("id", existingDevice.id!!) }
                }
            } else {
                val device = UserDevice(
                    userId = currentUser.id,
                    fcmToken = token,
                    deviceName = deviceName,
                    appVersion = appVersion,
                    isActive = true,
                    lastSeenAt = java.time.Instant.now().toString()
                )
                userDevicesTable.insert(device)
            }
            Log.d("DeviceRepository", "Device registered successfully")
        } catch (e: Exception) {
            Log.e("DeviceRepository", "Failed to register device", e)
        }
    }

    override suspend fun deactivateCurrentDevice() {
        val token = getFcmToken() ?: return

        try {
            userDevicesTable.update(
                mapOf("is_active" to false)
            ) {
                filter {
                    eq("fcm_token", token)
                }
            }
        } catch (e: Exception) {
            // Device registration is best-effort. A network/Supabase failure must
            // never prevent logout from completing.
            Log.w("DeviceRepository", "Could not deactivate device", e)
        }
    }

    override suspend fun updateActivity() {
        val token = getFcmToken() ?: return

        try {
            userDevicesTable.update(
                mapOf(
                    "last_seen_at" to java.time.Instant.now().toString(),
                    "app_version" to getAppVersion()
                )
            ) {
                filter {
                    eq("fcm_token", token)
                }
            }
        } catch (e: Exception) {
            // Device heartbeat is non-critical. In particular, a timeout here must
            // not abort MainActivity.onResume() before the normal data sync is queued.
            Log.w("DeviceRepository", "Could not update device activity; continuing", e)
        }
    }

    private suspend fun getFcmToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            null
        }
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}
