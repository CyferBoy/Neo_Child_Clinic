package com.neochildclinic.feature.auth.domain.model

sealed interface AuthSessionStatus {
    data object Initializing : AuthSessionStatus
    data class Authenticated(val user: AuthUser) : AuthSessionStatus
    data object NotAuthenticated : AuthSessionStatus
    data class RefreshFailure(val cause: Throwable? = null) : AuthSessionStatus
}
