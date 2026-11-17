package com.neochildclinic.feature.auth.domain.model

import com.neochildclinic.domain.model.UserRole

data class AuthUser(
    val id: String,
    val email: String?,
    val lastSignInAt: String? = null,
    val role: UserRole? = null
)
