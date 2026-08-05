package com.neochildclinic.domain.repository

import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    val allProfiles: Flow<List<Profile>>
    
    suspend fun getProfileById(id: String): Profile?
    suspend fun refreshProfiles()
    suspend fun updateProfile(profile: Profile)
    suspend fun toggleProfileStatus(id: String, isActive: Boolean)
    suspend fun updateProfileRole(id: String, role: UserRole)
    suspend fun deleteProfile(id: String)
    suspend fun saveLocalProfile(profile: Profile)
}
