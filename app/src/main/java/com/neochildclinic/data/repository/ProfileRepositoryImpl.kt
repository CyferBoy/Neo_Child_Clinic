package com.neochildclinic.data.repository

import com.neochildclinic.data.local.dao.ProfileDao
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.data.local.entity.toEntity
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.domain.repository.ProfileRepository
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.neochildclinic.data.cache.MemoryCache

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao,
    private val postgrest: Postgrest,
    private val syncRepository: SyncRepository,
    private val memoryCache: MemoryCache
) : ProfileRepository {

    override val allProfiles: Flow<List<Profile>> = 
        profileDao.getAllProfiles().map { list -> list.map { it.toDomain() } }

    override suspend fun getProfileById(id: String): Profile? {
        memoryCache.getProfile(id)?.let { return it }
        return profileDao.getProfileById(id)?.toDomain()?.also { memoryCache.putProfile(it) }
    }

    override suspend fun refreshProfiles() {
        try {
            val profiles = postgrest.from("profiles").select { filter { eq("is_deleted", false) } }.decodeList<Profile>()
            profiles.forEach { profile ->
                profileDao.insertProfile(profile.toEntity())
                memoryCache.putProfile(profile)
            }
        } catch (e: Exception) {
            android.util.Log.e("ProfileRepo", "Failed to refresh profiles", e)
        }
    }

    override suspend fun updateProfile(profile: Profile) {
        profileDao.insertProfile(profile.toEntity())
        memoryCache.putProfile(profile)
        syncRepository.enqueue(
            entityName = "PROFILE",
            entityId = profile.id,
            operation = SyncOperation.UPDATE,
            priority = SyncPriority.HIGH
        )
    }

    override suspend fun toggleProfileStatus(id: String, isActive: Boolean) {
        val profile = getProfileById(id) ?: return
        val updated = profile.copy(isActive = isActive)
        updateProfile(updated)
    }

    override suspend fun updateProfileRole(id: String, role: UserRole) {
        val profile = getProfileById(id) ?: return
        val updated = profile.copy(role = role)
        updateProfile(updated)
    }

    override suspend fun deleteProfile(id: String) {
        profileDao.deleteProfile(id)
        memoryCache.invalidateProfile(id)
        syncRepository.enqueue(
            entityName = "PROFILE",
            entityId = id,
            operation = SyncOperation.DELETE,
            priority = SyncPriority.MEDIUM
        )
    }

    override suspend fun saveLocalProfile(profile: Profile) {
        profileDao.insertProfile(profile.toEntity())
        memoryCache.putProfile(profile)
    }
}
