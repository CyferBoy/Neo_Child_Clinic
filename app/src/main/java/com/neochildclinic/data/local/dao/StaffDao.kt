package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.StaffEntity
import com.neochildclinic.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StaffDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(staff: StaffEntity)

    @Query("SELECT * FROM staff_profiles WHERE id = :id LIMIT 1")
    suspend fun getStaffById(id: String): StaffEntity?

    @Query("SELECT * FROM staff_profiles WHERE isActive = 1")
    fun getActiveStaff(): Flow<List<StaffEntity>>

    @Query("SELECT * FROM staff_profiles WHERE email = :email LIMIT 1")
    suspend fun getStaffByEmail(email: String): StaffEntity?

    @Query("DELETE FROM staff_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)

    // User Auth Support
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM user_auth WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?
}
