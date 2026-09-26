package com.neochildclinic.data.repository

import io.github.jan.supabase.functions.Functions
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delegates staff lifecycle operations to the Supabase `manage-staff` edge function
 * (create account, soft-delete, reset password email, role/status/profile changes).
 */
@Singleton
class StaffManagementRepositoryImpl @Inject constructor(
    private val functions: Functions
) {
    suspend fun createStaff(req: CreateStaffRequest) {
        functions.invoke("manage-staff", req)
    }

    suspend fun runStaffAction(req: StaffActionRequest) {
        functions.invoke("manage-staff", req)
    }
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