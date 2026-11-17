package com.neochildclinic.core.security

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfflineAuthManagerTest {

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(data)
        override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (data[key] as? MutableSet<String> ?: defValues)
        override fun getInt(key: String, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val target: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private val toRemove = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                temp[key] = value
                toRemove.remove(key)
                return this
            }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
                temp[key] = values
                toRemove.remove(key)
                return this
            }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                temp[key] = value
                toRemove.remove(key)
                return this
            }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                temp[key] = value
                toRemove.remove(key)
                return this
            }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                temp[key] = value
                toRemove.remove(key)
                return this
            }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                temp[key] = value
                toRemove.remove(key)
                return this
            }
            override fun remove(key: String): SharedPreferences.Editor {
                toRemove.add(key)
                temp.remove(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) target.clear()
                for (r in toRemove) target.remove(r)
                for ((k, v) in temp) {
                    if (v == null) target.remove(k) else target[k] = v
                }
            }
        }
    }

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var manager: OfflineAuthManager

    @Before
    fun setup() {
        fakePrefs = FakeSharedPreferences()
        manager = OfflineAuthManager { fakePrefs }
    }

    @Test
    fun saveCredentialAndVerifyPasswordSuccess() {
        manager.saveCredential("doctor@clinic.com", "CorrectPassword123")
        assertTrue(manager.hasEnrolledCredential("doctor@clinic.com"))
        assertTrue(manager.hasEnrolledCredential("DOCTOR@CLINIC.COM"))
        assertTrue(manager.verifyPassword("doctor@clinic.com", "CorrectPassword123"))
    }

    @Test
    fun verifyPasswordFailureOnWrongPassword() {
        manager.saveCredential("doctor@clinic.com", "CorrectPassword123")
        assertFalse(manager.verifyPassword("doctor@clinic.com", "WrongPassword"))
        assertFalse(manager.verifyPassword("other@clinic.com", "CorrectPassword123"))
    }

    @Test
    fun exponentialLockoutAfterFiveFailedAttempts() {
        manager.saveCredential("doctor@clinic.com", "Password123")
        repeat(4) {
            assertFalse(manager.verifyPassword("doctor@clinic.com", "bad$it"))
        }
        // 5th failed attempt triggers lockout
        assertFalse(manager.verifyPassword("doctor@clinic.com", "bad5"))

        // Next attempt throws IllegalStateException due to active lockout
        try {
            manager.verifyPassword("doctor@clinic.com", "Password123")
            org.junit.Assert.fail("Expected IllegalStateException due to lockout")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Too many failed attempts") == true)
        }
    }

    @Test
    fun clearCredentialRemovesStoredData() {
        manager.saveCredential("doctor@clinic.com", "Password123")
        assertTrue(manager.hasEnrolledCredential("doctor@clinic.com"))
        manager.clearCredential()
        assertFalse(manager.hasEnrolledCredential("doctor@clinic.com"))
        assertFalse(manager.verifyPassword("doctor@clinic.com", "Password123"))
    }
}
