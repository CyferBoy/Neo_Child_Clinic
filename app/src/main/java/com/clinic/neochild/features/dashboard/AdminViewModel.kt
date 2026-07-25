package com.clinic.neochild.features.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import com.clinic.neochild.domain.model.Staff
import com.clinic.neochild.data.remote.mapper.FirestoreMappers
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class AdminUiState(
    val staffList: List<Staff> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: String? = null
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val db: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        fetchStaff()
    }

    fun fetchStaff() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        // Fetch from 'staff' collection (profiles with roles)
        db.collection("staff").get().addOnSuccessListener { staffResult ->
            val staffProfiles = staffResult.documents.mapNotNull { FirestoreMappers.toStaff(it) }
            
            // Also fetch from 'users' collection (all users who ever logged in/registered tokens)
            db.collection("users").get().addOnSuccessListener { usersResult ->
                val authUsers = usersResult.documents.mapNotNull { doc ->
                    val email = doc.getString("email") ?: ""
                    if (email.isBlank()) return@mapNotNull null
                    
                    // If this user isn't already in staffProfiles, create a basic entry
                    if (staffProfiles.none { it.email.equals(email, ignoreCase = true) }) {
                        Staff(
                            id = doc.id,
                            email = email,
                            name = email.split("@").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "User",
                            role = "Pending Approval",
                            fcmToken = doc.getString("fcmToken"),
                            createdAt = 0L
                        )
                    } else null
                }
                
                val combinedList = (staffProfiles + authUsers).sortedBy { it.name }
                _uiState.value = _uiState.value.copy(staffList = combinedList, isLoading = false)
            }.addOnFailureListener {
                // If users fetch fails, just show staff
                _uiState.value = _uiState.value.copy(staffList = staffProfiles.sortedBy { it.name }, isLoading = false)
            }
        }.addOnFailureListener {
            _uiState.value = _uiState.value.copy(error = it.message, isLoading = false)
        }
    }

    fun createStaffAccount(name: String, email: String, pass: String) {
        if (name.isBlank() || email.isBlank() || pass.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please fill all fields")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null, success = null)

        val currentApp = FirebaseApp.getInstance()
        val options = currentApp.options
        val secondaryAppName = "SecondaryRegisterApp"
        
        var isNewApp = false
        val secondaryApp = try {
            FirebaseApp.getInstance(secondaryAppName)
        } catch (e: Exception) {
            isNewApp = true
            FirebaseApp.initializeApp(currentApp.applicationContext, options, secondaryAppName)
        }

        if (isNewApp) {
            try {
                val appCheck = FirebaseAppCheck.getInstance(secondaryApp)
                // Use Debug provider to match the primary app's configuration and avoid broker security issues in debug
                val factory = DebugAppCheckProviderFactory.getInstance()
                appCheck.installAppCheckProviderFactory(factory)
                Log.d("AdminViewModel", "App Check initialized with Debug provider for secondary app")
            } catch (e: Exception) {
                Log.e("AdminViewModel", "App Check initialization failed for secondary app: ${e.message}")
            }
        }

        val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)

        secondaryAuth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid ?: ""
                val staff = Staff(
                    id = uid,
                    name = name,
                    email = email,
                    role = "Staff",
                    createdAt = System.currentTimeMillis()
                )

                db.collection("staff").document(uid).set(staff)
                    .addOnSuccessListener {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            success = "Staff account created successfully!"
                        )
                        fetchStaff()
                        secondaryAuth.signOut()
                    }
                    .addOnFailureListener {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Auth created but Firestore failed: ${it.message}"
                        )
                    }
            }
            .addOnFailureListener {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
    }

    fun deleteStaff(staffId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        // Delete from 'staff'
        db.collection("staff").document(staffId).delete()
            .addOnSuccessListener {
                // Also delete from 'users' (FCM tokens etc)
                db.collection("users").document(staffId).delete()
                    .addOnCompleteListener {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            success = "Staff and user data deleted successfully"
                        )
                        fetchStaff()
                    }
            }
            .addOnFailureListener {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
    }

    fun resetStaffPassword(email: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, success = null)
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnSuccessListener {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    success = "Password reset email sent to $email"
                )
            }
            .addOnFailureListener {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
    }

    fun updateStaffRole(staffId: String, newRole: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, success = null)
        db.collection("staff").document(staffId).set(mapOf("role" to newRole), com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    success = "Role updated to $newRole"
                )
                fetchStaff()
            }
            .addOnFailureListener {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, success = null)
    }
}
