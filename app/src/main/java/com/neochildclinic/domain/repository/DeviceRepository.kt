package com.neochildclinic.domain.repository

interface DeviceRepository {
    suspend fun registerCurrentDevice()
    suspend fun deactivateCurrentDevice()
    suspend fun updateActivity()
}
