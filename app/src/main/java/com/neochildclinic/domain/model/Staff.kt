package com.neochildclinic.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Staff(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "Staff",
    val fcmToken: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
