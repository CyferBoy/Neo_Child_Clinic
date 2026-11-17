package com.neochildclinic.feature.auth.domain.repository

import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.feature.auth.domain.model.AuthSessionStatus
import com.neochildclinic.feature.auth.domain.model.AuthUser
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val currentUser: AuthUser?
    val sessionStatus: StateFlow<AuthSessionStatus>
    val currentUserRole: UserRole?

    suspend fun awaitResolvedSessionStatus(timeoutMs: Long = 4_000L): AuthSessionStatus?
    suspend fun login(email: String, pass: String): Result<AuthUser>
    suspend fun signOut(): Result<Unit>
    fun clearSession()
    suspend fun reauthenticateWithPassword(password: String): Result<Unit>
    suspend fun updatePassword(newPassword: String): Result<Unit>
    suspend fun resetPasswordForEmail(email: String): Result<Unit>
}
