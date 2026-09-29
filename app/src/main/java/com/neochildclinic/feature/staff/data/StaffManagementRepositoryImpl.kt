package com.neochildclinic.feature.staff.data

import com.neochildclinic.feature.staff.domain.repository.CreateStaffRequest
import com.neochildclinic.feature.staff.domain.repository.StaffActionRequest
import com.neochildclinic.feature.staff.domain.repository.StaffManagementRepository
import io.github.jan.supabase.functions.Functions
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StaffManagementRepositoryImpl @Inject constructor(
    private val functions: Functions
) : StaffManagementRepository {

    override suspend fun createStaff(req: CreateStaffRequest) {
        functions.invoke("manage-staff", req)
    }

    override suspend fun runStaffAction(req: StaffActionRequest) {
        functions.invoke("manage-staff", req)
    }
}