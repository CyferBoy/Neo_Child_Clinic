package com.neochildclinic.feature.auth.data.repository

import com.neochildclinic.core.network.NetworkMonitor
import com.neochildclinic.core.security.CurrentUserProvider
import com.neochildclinic.core.security.OfflineAuthManager
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.feature.auth.domain.model.AuthSessionStatus
import com.neochildclinic.feature.auth.domain.model.AuthUser
import com.neochildclinic.feature.auth.domain.repository.AuthRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: Auth,
    private val currentUserProvider: CurrentUserProvider,
    private val offlineAuthManager: OfflineAuthManager,
    private val networkMonitor: NetworkMonitor
) : AuthRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val currentUser: AuthUser?
        get() = auth.currentSessionOrNull()?.user?.toDomain(currentUserProvider.getCurrentUserRole())

    override val sessionStatus: StateFlow<AuthSessionStatus> = auth.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Initializing -> AuthSessionStatus.Initializing
                is SessionStatus.Authenticated -> {
                    val user = auth.currentSessionOrNull()?.user?.toDomain(currentUserProvider.getCurrentUserRole())
                        ?: AuthUser(id = "", email = null)
                    AuthSessionStatus.Authenticated(user)
                }
                is SessionStatus.NotAuthenticated -> AuthSessionStatus.NotAuthenticated
                is SessionStatus.RefreshFailure -> AuthSessionStatus.RefreshFailure(
                    when (val cause = status.cause) {
                        is RefreshFailureCause.NetworkError -> cause.exception
                        is RefreshFailureCause.InternalServerError -> cause.exception
                    }
                )
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = AuthSessionStatus.Initializing
        )

    override val currentUserRole: UserRole?
        get() = currentUserProvider.getCurrentUserRole()

    override suspend fun awaitResolvedSessionStatus(timeoutMs: Long): AuthSessionStatus? {
        return withTimeoutOrNull(timeoutMs) {
            sessionStatus.first { it !is AuthSessionStatus.Initializing }
        }
    }

    override suspend fun login(email: String, pass: String): Result<AuthUser> = runCatching {
        auth.signInWith(Email) {
            this.email = email
            this.password = pass
        }
        offlineAuthManager.saveCredential(email, pass)
        offlineAuthManager.recordOnlineVerification()
        val user = auth.currentSessionOrNull()?.user
            ?: throw IllegalStateException("Supabase session resolved without a user.")
        user.toDomain(currentUserProvider.getCurrentUserRole())
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        try {
            auth.signOut()
        } finally {
            auth.clearSession()
        }
    }

    override fun clearSession() {
        scope.launch { runCatching { auth.clearSession() } }
    }

    override suspend fun reauthenticateWithPassword(password: String): Result<Unit> = runCatching {
        val email = auth.currentSessionOrNull()?.user?.email
        if (email.isNullOrBlank()) {
            throw IllegalStateException("No account email is available.")
        }
        val isOnline = networkMonitor.isOnline.first()
        if (isOnline) {
            try {
                auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                offlineAuthManager.saveCredential(email, password)
                offlineAuthManager.recordOnlineVerification()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (com.neochildclinic.core.sync.isTransientSyncError(e)) {
                    verifyOfflineOrThrow(email, password)
                } else {
                    throw e
                }
            }
        } else {
            verifyOfflineOrThrow(email, password)
        }
    }

    /**
     * SE-01: the local password verifier alone is not proof the account is still active.
     * It is only consulted while a recent online check backs it.
     */
    private fun verifyOfflineOrThrow(email: String, password: String) {
        if (!offlineAuthManager.isOfflineUnlockAllowed()) {
            throw IllegalStateException("Offline access has expired. Connect to the internet and sign in again.")
        }
        if (!offlineAuthManager.verifyPassword(email, password)) {
            throw IllegalArgumentException("Incorrect account password.")
        }
    }

    override suspend fun updatePassword(newPassword: String): Result<Unit> = runCatching {
        auth.updateUser {
            this.password = newPassword
        }
    }

    override suspend fun resetPasswordForEmail(email: String): Result<Unit> = runCatching {
        auth.resetPasswordForEmail(email)
    }

    private fun UserInfo.toDomain(role: UserRole?): AuthUser =
        AuthUser(
            id = id,
            email = email,
            lastSignInAt = lastSignInAt?.toString(),
            role = role
        )
}
