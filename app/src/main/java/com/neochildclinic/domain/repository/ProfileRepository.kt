package com.neochildclinic.domain.repository

import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    val allProfiles: Flow<List<Profile>>
    
    suspend fun getProfileById(id: String): Profile?
    // Fetches a single profile straight from the profiles table (source of truth for
    // role), bypassing Room/memory cache. Used to bootstrap a profile authoritatively
    // instead of guessing the role from Supabase Auth user_metadata. Returns null if
    // offline or the row doesn't exist (e.g. brand-new signup not yet provisioned).
    suspend fun fetchProfileFromRemote(id: String): Profile?
    suspend fun refreshProfiles()
    suspend fun updateProfile(profile: Profile)
    suspend fun toggleProfileStatus(id: String, isActive: Boolean)
    suspend fun updateProfileRole(id: String, role: UserRole)
    suspend fun deleteProfile(id: String)
    suspend fun saveLocalProfile(profile: Profile)
}
