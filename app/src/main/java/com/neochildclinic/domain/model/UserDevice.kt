package com.neochildclinic.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDevice(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("fcm_token") val fcmToken: String,
    @SerialName("device_name") val deviceName: String,
    val platform: String = "Android",
    @SerialName("app_version") val appVersion: String,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
