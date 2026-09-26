package com.neochildclinic.domain.repository

import kotlinx.serialization.Serializable

/**
 * Staff lifecycle operations delegated to the Supabase `manage-staff` edge function
 * (create account, soft-delete, reset password email, role/status/profile changes).
 */
interface StaffManagementRepository {
    suspend fun createStaff(req: CreateStaffRequest)
    suspend fun runStaffAction(req: StaffActionRequest)
}

@Serializable
data class StaffActionRequest(
    val action: String,
    val staffId: String? = null,
    val name: String? = null,
    val email: String? = null,
    val password: String? = null,
    val role: String? = null,
    val employeeId: String? = null,
    val phoneNumber: String? = null,
    val isActive: Boolean? = null
)

@Serializable
data class CreateStaffRequest(
    val name: String,
    val email: String,
    val password: String,
    val role: String,
    val employeeId: String? = null,
    val phoneNumber: String? = null,
    val action: String = "CREATE"
)