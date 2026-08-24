package com.neochildclinic.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    admin, doctor, receptionist, nurse, inventory_manager
}

@Serializable
data class Profile(
    val id: String = "",
    val email: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("phone_number") val phoneNumber: String = "",
    val role: UserRole = UserRole.nurse,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("employee_id") val employeeId: String? = null,
    @SerialName("fcm_token") val fcmToken: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("last_login") val lastLogin: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("updated_by") val updatedBy: String? = null
)
