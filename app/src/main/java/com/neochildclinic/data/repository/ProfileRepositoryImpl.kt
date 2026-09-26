package com.neochildclinic.data.repository

import com.neochildclinic.data.local.dao.ProfileDao
import com.neochildclinic.data.local.dao.SyncQueueDao
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.data.local.entity.toEntity
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.repository.ProfileRepository
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao,
    private val syncQueueDao: SyncQueueDao,
    private val postgrest: Postgrest,
    private val syncRepository: SyncRepository,
    private val auth: Auth
) : ProfileRepository {

    override val allProfiles: Flow<List<Profile>> = 
        profileDao.getAllProfiles().map { list -> list.map { it.toDomain() } }

    override suspend fun getProfileById(id: String): Profile? =
        profileDao.getProfileById(id)?.toDomain()

    override suspend fun getProfileByEmail(email: String): Profile? {
        return try {
            postgrest.from("profiles")
                .select { filter { eq("email", email); eq("is_deleted", false) } }
                .decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            android.util.Log.e("ProfileRepo", "Failed to fetch profile by email $email", e)
            null
        }
    }

    override suspend fun fetchProfileFromRemote(id: String): Profile? {
        return try {
            postgrest.from("profiles")
                .select { filter { eq("id", id); eq("is_deleted", false) } }
                .decodeSingleOrNull<Profile>()
                ?.also {
                    // profiles has no isSynced column - the queue is the only local-pending
                    // signal. Never clobber a row that still has a local edit waiting to upload.
                    if (!syncQueueDao.isUnsynced("PROFILE", it.id)) {
                        profileDao.insertProfile(it.toEntity())
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("ProfileRepo", "Failed to fetch profile $id from remote", e)
            null
        }
    }

    override suspend fun refreshProfiles() = cloudRefresh("ProfileRepo") {
        val profiles = postgrest.from("profiles").select { filter { eq("is_deleted", false) } }.decodeList<Profile>()
        profiles.forEach { profile ->
            if (!syncQueueDao.isUnsynced("PROFILE", profile.id)) {
                profileDao.insertProfile(profile.toEntity())
            }
        }
    }

    override suspend fun updateProfile(profile: Profile) {
        profileDao.insertProfile(profile.toEntity())
        syncRepository.enqueue(
            entityName = "PROFILE",
            entityId = profile.id,
            operation = SyncOperation.UPDATE,
            priority = SyncPriority.HIGH
        )
    }

override suspend fun saveLocalProfile(profile: Profile) {
    profileDao.insertProfile(profile.toEntity())
}

override suspend fun updateAuthName(name: String) {
        auth.updateUser {
            data {
                put("name", name)
            }
        }
    }

    override suspend fun updateAuthPassword(newPassword: String) {
        auth.updateUser {
            password = newPassword
        }
    }
}
