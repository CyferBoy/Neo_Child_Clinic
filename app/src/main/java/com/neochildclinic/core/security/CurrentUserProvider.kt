package com.neochildclinic.core.security

import com.neochildclinic.core.common.metadataString
import com.neochildclinic.domain.model.UserRole
import io.github.jan.supabase.auth.Auth
import kotlinx.serialization.json.JsonObject

/**
 * Single source of truth for the signed-in user's authorization role.
 * Authorization only; UI gates built on it are UX — RLS enforces independently.
 */
interface CurrentUserProvider {
    /**
     * Role from the current session's `app_metadata` — the same claim RLS reads via
     * `auth.jwt() -> 'app_metadata' ->> 'role'`. Null when signed out, the session is
     * not yet resolved, or the value is missing/unknown/malformed. Never defaults.
     */
    fun getCurrentUserRole(): UserRole?
}

/** `app_metadata.role` -> [UserRole]; case-sensitive exact match, null for anything else. */
fun roleFromAppMetadata(meta: JsonObject?): UserRole? =
    meta?.get("role").metadataString()?.let { raw -> UserRole.entries.firstOrNull { it.name == raw } }

/**
 * Reads the live Supabase session on every call — deliberately uncached, so login,
 * logout, token refresh and expiry are reflected at the next read. Never introduces
 * an authentication state of its own.
 */
class SessionCurrentUserProvider internal constructor(
    private val appMetadata: () -> JsonObject?
) : CurrentUserProvider {

    constructor(auth: Auth) : this({ auth.currentSessionOrNull()?.user?.appMetadata })

    override fun getCurrentUserRole(): UserRole? = roleFromAppMetadata(appMetadata())
}
