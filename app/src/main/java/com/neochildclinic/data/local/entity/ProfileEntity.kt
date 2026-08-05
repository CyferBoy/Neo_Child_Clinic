package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.UserRole
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "profiles",
    indices = [Index("email", unique = true)]
)
data class ProfileEntity(
    @PrimaryKey val id: String,
    val email: String,
    val displayName: String,
    val phoneNumber: String = "",
    val employeeId: String? = null,
    val role: String, // Enum name
    val isActive: Boolean = true,
    val fcmToken: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val lastLogin: String? = null
)

fun ProfileEntity.toDomain() = Profile(
    id = id,
    email = email,
    displayName = displayName,
    phoneNumber = phoneNumber,
    employeeId = employeeId,
    role = UserRole.valueOf(role),
    isActive = isActive,
    fcmToken = fcmToken,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastLogin = lastLogin
)

fun Profile.toEntity() = ProfileEntity(
    id = id,
    email = email,
    displayName = displayName,
    phoneNumber = phoneNumber,
    employeeId = employeeId,
    role = role.name,
    isActive = isActive,
    fcmToken = fcmToken,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastLogin = lastLogin
)
