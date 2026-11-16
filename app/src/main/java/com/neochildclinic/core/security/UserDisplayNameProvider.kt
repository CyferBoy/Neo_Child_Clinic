package com.neochildclinic.core.security

/**
 * Interface abstraction allowing core security and session managers to query
 * a user's display name without coupling core to feature-layer profile repositories.
 */
interface UserDisplayNameProvider {
    suspend fun getUserDisplayName(userId: String): String?
}
