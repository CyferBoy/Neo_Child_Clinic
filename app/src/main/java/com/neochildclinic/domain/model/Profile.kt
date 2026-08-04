package com.neochildclinic.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    admin, doctor, receptionist, nurse, inventory_manager
}

@Serializable
data class Profile(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val phoneNumber: String = "",
    val role: UserRole = UserRole.nurse,
    val isActive: Boolean = true,
    val employeeId: String? = null,
    val fcmToken: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val lastLogin: String? = null
)
