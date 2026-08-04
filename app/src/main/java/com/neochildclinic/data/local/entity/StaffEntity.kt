package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "staff_profiles",
    indices = [Index("email", unique = true)]
)
data class StaffEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val role: String, // ADMIN, DOCTOR, NURSE, STAFF
    val department: String? = null,
    val permissions: String? = null, // JSON list
    val fcmToken: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActive: Long = System.currentTimeMillis()
)
