package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "user_auth",
    indices = [Index("email", unique = true)]
)
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val name: String,
    val biometricEnabled: Boolean = false,
    val pinHash: String? = null,
    val lastLogin: Long = 0,
    val fcmToken: String? = null,
    val devices: String? = null, // JSON list of device IDs
    val securityStamp: String = ""
)
