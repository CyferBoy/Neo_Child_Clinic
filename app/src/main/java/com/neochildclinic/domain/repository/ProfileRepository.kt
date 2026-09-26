package com.neochildclinic.domain.repository

import com.neochildclinic.domain.model.Profile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    val allProfiles: Flow<List<Profile>>
    suspend fun getProfileById(id: String): Profile?
    suspend fun getProfileByEmail(email: String): Profile?
    suspend fun fetchProfileFromRemote(id: String): Profile?
    suspend fun refreshProfiles()
    suspend fun updateProfile(profile: Profile)
    suspend fun saveLocalProfile(profile: Profile)
    suspend fun updateAuthName(name: String)
    suspend fun updateAuthPassword(newPassword: String)
}