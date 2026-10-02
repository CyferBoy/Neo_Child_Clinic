package com.neochildclinic.core.security

import com.neochildclinic.domain.model.UserRole
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrentUserProviderTest {

    private fun meta(role: String): JsonObject = buildJsonObject { put("role", role) }

    // --- roleFromAppMetadata: parsing ---
    @Test fun parsesAdmin() {
        assertEquals(UserRole.admin, roleFromAppMetadata(meta("admin")))
    }

    @Test fun parsesDoctor() {
        assertEquals(UserRole.doctor, roleFromAppMetadata(meta("doctor")))
    }

    @Test fun parsesAllFiveRoles() {
        UserRole.entries.forEach { role ->
            assertEquals(role, roleFromAppMetadata(meta(role.name)))
        }
        assertEquals(
            setOf("admin", "doctor", "receptionist", "nurse", "inventory_manager"),
            UserRole.entries.map { it.name }.toSet()
        )
    }

    @Test fun missingRoleKeyReturnsNull() {
        assertNull(roleFromAppMetadata(buildJsonObject { put("display_name", "x") }))
    }

    @Test fun nullMetadataReturnsNull() {
        assertNull(roleFromAppMetadata(null))
    }

    @Test fun unknownRoleReturnsNull() {
        assertNull(roleFromAppMetadata(meta("superuser")))
    }

    @Test fun nonStringRoleReturnsNull() {
        assertNull(roleFromAppMetadata(buildJsonObject { put("role", 42) }))
    }

    @Test fun quotedJsonStringRoleReturnsNull() {
        // Historical bug: reading a JsonElement with .toString() produced `"admin"`
        // (with literal quote characters). That must never map to a role.
        assertNull(roleFromAppMetadata(buildJsonObject { put("role", "\"admin\"") }))
    }

    // --- SessionCurrentUserProvider: session-derived, uncached ---
    @Test fun signedOutYieldsNull() {
        val provider = SessionCurrentUserProvider { null }
        assertNull(provider.getCurrentUserRole())
    }

    @Test fun roleReadFromCurrentSession() {
        val provider = SessionCurrentUserProvider { meta("admin") }
        assertEquals(UserRole.admin, provider.getCurrentUserRole())
    }

    @Test fun roleFollowsSessionChange() {
        var sessionMeta: JsonObject? = meta("doctor")
        val provider = SessionCurrentUserProvider { sessionMeta }
        assertEquals(UserRole.doctor, provider.getCurrentUserRole())

        sessionMeta = meta("admin")          // token refresh picked up a role change
        assertEquals(UserRole.admin, provider.getCurrentUserRole())

        sessionMeta = null                   // logout / expired session
        assertNull(provider.getCurrentUserRole())
    }
}
