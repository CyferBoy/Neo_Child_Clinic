package com.neochildclinic.core.session

import com.neochildclinic.core.utils.metadataString
import com.neochildclinic.data.repository.ProfileRepositoryImpl
import io.github.jan.supabase.auth.Auth
import kotlinx.serialization.json.JsonObject
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

    /**
     * Returns the current user's login email, matching the "Unknown" fallback
     * callers previously read straight off `auth.currentSessionOrNull()`.
     */
    fun getCurrentUserEmail(): String {
        return auth.currentSessionOrNull()?.user?.email ?: "Unknown"
    }

    /**
     * Returns the actor name for audit stamps: the user's display name from
     * auth metadata, falling back to email, then `fallback`.
     */
    fun getCurrentUserDisplayName(fallback: String = "Unknown"): String {
        val user = auth.currentSessionOrNull()?.user ?: return fallback
        return user.userMetadata?.get("name").metadataString() ?: user.email ?: fallback
    }

    /**
     * Returns the signed-in user's auth metadata, or null when signed out.
     */
    fun getCurrentUserMetadata(): JsonObject? {
        return auth.currentSessionOrNull()?.user?.userMetadata
    }
}
