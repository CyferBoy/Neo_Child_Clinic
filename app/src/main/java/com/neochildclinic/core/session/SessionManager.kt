package com.neochildclinic.core.session

import com.neochildclinic.data.repository.ProfileRepositoryImpl
import io.github.jan.supabase.auth.Auth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val auth: Auth,
    private val profileRepository: ProfileRepositoryImpl
) {
    /**
     * Returns the human-readable name of the current user.
     * Priority: Profile Display Name > Auth Email > "Unknown"
     */
    suspend fun getCurrentUserName(): String {
        val sessionUser = auth.currentSessionOrNull()?.user ?: return "Unknown"
        
        return try {
            profileRepository.getProfileById(sessionUser.id)?.displayName?.takeIf { it.isNotBlank() }
                ?: sessionUser.email
                ?: "Unknown"
        } catch (e: Exception) {
            sessionUser.email ?: "Unknown"
        }
    }

    /**
     * Returns the current user's ID.
     */
    fun getCurrentUserId(): String? {
        return auth.currentSessionOrNull()?.user?.id
    }
}
