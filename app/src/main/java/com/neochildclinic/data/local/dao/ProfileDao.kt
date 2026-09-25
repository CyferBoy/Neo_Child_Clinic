package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    @Query("SELECT * FROM profiles WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getProfileById(id: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE is_deleted = 0 ORDER BY displayName ASC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE isActive = 1 AND is_deleted = 0")
    fun getActiveProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE email = :email AND is_deleted = 0 LIMIT 1")
    suspend fun getProfileByEmail(email: String): ProfileEntity?

    @Query("UPDATE profiles SET is_deleted = 1, deleted_at = :deletedAt, deleted_by = :deletedBy, isActive = 0, updatedAt = :deletedAt, updated_by = :deletedBy WHERE id = :id AND is_deleted = 0")
    suspend fun softDeleteProfile(id: String, deletedAt: String, deletedBy: String?)
}
