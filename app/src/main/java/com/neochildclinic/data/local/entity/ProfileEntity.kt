package com.neochildclinic.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.UserRole
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
@Entity(
    tableName = "profiles",
    indices = [Index("email", unique = true)]
)
data class ProfileEntity(
    @PrimaryKey val id: String,
    val email: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("phone_number") val phoneNumber: String = "",
    @SerialName("employee_id") val employeeId: String? = null,
    val role: String, // Enum name
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("fcm_token") val fcmToken: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("last_login") val lastLogin: String? = null,
    @ColumnInfo(name = "created_by") val createdBy: String? = null,
    @ColumnInfo(name = "updated_by") val updatedBy: String? = null
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
    lastLogin = lastLogin,
    createdBy = createdBy,
    updatedBy = updatedBy
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
    lastLogin = lastLogin,
    createdBy = createdBy,
    updatedBy = updatedBy
)
