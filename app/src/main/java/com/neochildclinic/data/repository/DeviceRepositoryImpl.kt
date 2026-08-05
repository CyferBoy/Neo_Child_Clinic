package com.neochildclinic.data.repository

import android.content.Context
import android.os.Build
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

@Singleton
class DeviceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: Auth,
    private val postgrest: Postgrest
) : DeviceRepository {

    private val userDevicesTable = postgrest.from("user_devices")

    override suspend fun registerCurrentDevice() {
        val currentUser = auth.currentSessionOrNull()?.user ?: return
        val token = getFcmToken() ?: return
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        val appVersion = getAppVersion()

        // Check if device already registered with this token
        val existingDevice = userDevicesTable.select {
            filter {
                eq("fcm_token", token)
            }
        }.decodeSingleOrNull<UserDevice>()

        val device = UserDevice(
            id = existingDevice?.id,
            userId = currentUser.id,
            fcmToken = token,
            deviceName = deviceName,
            appVersion = appVersion,
            isActive = true
        )

        userDevicesTable.upsert(device)
    }

    override suspend fun deactivateCurrentDevice() {
        val token = getFcmToken() ?: return
        
        userDevicesTable.update(
            mapOf("is_active" to false)
        ) {
            filter {
                eq("fcm_token", token)
            }
        }
    }

    override suspend fun updateActivity() {
        val token = getFcmToken() ?: return
        
        userDevicesTable.update(
            mapOf(
                "last_seen_at" to "now()",
                "app_version" to getAppVersion()
            )
        ) {
            filter {
                eq("fcm_token", token)
            }
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
