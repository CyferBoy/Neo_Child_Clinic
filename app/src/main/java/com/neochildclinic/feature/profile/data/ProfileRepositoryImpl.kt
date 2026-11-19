package com.neochildclinic.feature.profile.data

import com.neochildclinic.data.local.dao.ProfileDao
import com.neochildclinic.core.database.TransactionRunner
import com.neochildclinic.data.local.dao.SyncQueueDao
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.data.local.entity.toEntity
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.repository.ProfileRepository
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton
import com.neochildclinic.core.security.UserDisplayNameProvider
import com.neochildclinic.core.sync.cloudRefresh
import com.neochildclinic.core.sync.fetchAllPages
import com.neochildclinic.core.sync.RemotePrune

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: dagger.Lazy<ProfileDao>,
    private val syncQueueDao: dagger.Lazy<SyncQueueDao>,
    private val postgrest: dagger.Lazy<Postgrest>,
    private val syncRepository: dagger.Lazy<SyncRepository>,
    private val auth: dagger.Lazy<Auth>,
    private val transactionRunner: dagger.Lazy<TransactionRunner>
) : ProfileRepository, UserDisplayNameProvider {

    override suspend fun getUserDisplayName(userId: String): String? {
        return getProfileById(userId)?.displayName
    }

    override val allProfiles: Flow<List<Profile>> = 
        profileDao.get().getAllProfiles().map { list -> list.map { it.toDomain() } }

    override suspend fun getProfileById(id: String): Profile? =
        profileDao.get().getProfileById(id)?.toDomain()

    override suspend fun getProfileByEmail(email: String): Profile? {
        return try {
            postgrest.get().from("profiles")
                .select { filter { eq("email", email); eq("is_deleted", false) } }
                .decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            android.util.Log.e("ProfileRepo", "Failed to fetch profile by email (lookup failed)", e)
            null
        }
    }

    override suspend fun fetchProfileFromRemote(id: String): Profile? {
        return try {
            postgrest.get().from("profiles")
                .select { filter { eq("id", id); eq("is_deleted", false) } }
                .decodeSingleOrNull<Profile>()
                ?.also {
                    // profiles has no isSynced column - the queue is the only local-pending
                    // signal. Never clobber a row that still has a local edit waiting to upload.
                    if (!syncQueueDao.get().isUnsynced("PROFILE", it.id)) {
                        profileDao.get().insertProfile(it.toEntity())
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("ProfileRepo", "Failed to fetch profile from remote", e)
            null
        }
    }

    override suspend fun refreshProfiles() = cloudRefresh("ProfileRepo", rethrow = true) {
        val profiles = fetchAllPages { from, to ->
            postgrest.get().from("profiles")
                .select {
                    filter { eq("is_deleted", false) }
                    order("id", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<Profile>()
        }
        transactionRunner.get().run {
            // One bulk queue read per refresh instead of one per row (same semantics as the
            // per-row isUnsynced this replaces). Read the guard inside the transaction so a
            // queued edit committed here is visible before this pull overwrites it.
            val pendingProfileIds = syncQueueDao.get().unsyncedIdSet("PROFILE")
            profiles.forEach { profile ->
                if (profile.id !in pendingProfileIds) {
                    profileDao.get().insertProfile(profile.toEntity())
                }
            }
            // Remote-deletion propagation (NCC-023 / DA-01): profiles has no isSynced column, so
            // the queue guard alone protects rows whose edit is still local; any other row absent
            // from the remote set is a staff profile deleted elsewhere.
            RemotePrune.computeBatches(
                localIds = profileDao.get().getProfileIds(),
                syncedIds = null,
                remoteIds = profiles.mapTo(HashSet()) { it.id },
                pendingIds = pendingProfileIds
            ).flatten().forEach { profileDao.get().deleteProfileById(it) }
        }
    }

    override suspend fun updateProfile(profile: Profile) {
        // profiles has no isSynced column, so the queue row is the ONLY record that an edit is
        // pending - and refreshProfiles() overwrites any row the queue does not flag. Writing the
        // profile and enqueueing in two separate statements let a kill between them lose the edit
        // silently (it was reverted by the next pull). One transaction, like every other
        // repository write path.
        transactionRunner.get().run {
            profileDao.get().insertProfile(profile.toEntity())
            syncRepository.get().enqueue(
                entityName = "PROFILE",
                entityId = profile.id,
                operation = SyncOperation.UPDATE,
                priority = SyncPriority.HIGH
            )
        }
    }

    override suspend fun updateAuthName(name: String) {
        auth.get().updateUser {
            data {
                put("name", name)
            }
        }
    }

    override suspend fun updateAuthPassword(newPassword: String) {
        auth.get().updateUser {
            password = newPassword
        }
    }
}
