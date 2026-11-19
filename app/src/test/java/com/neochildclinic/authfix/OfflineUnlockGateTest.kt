package com.neochildclinic.authfix

import android.content.SharedPreferences
import com.neochildclinic.core.security.OfflineAuthManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** SE-01: the offline password verifier is only usable while an online check is fresh. */
class OfflineUnlockGateTest {

    private class FakePrefs : SharedPreferences {
        val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(data)
        override fun getString(key: String, def: String?): String? = data[key] as? String ?: def
        override fun getStringSet(key: String, def: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (data[key] as? MutableSet<String> ?: def)
        override fun getInt(key: String, def: Int): Int = data[key] as? Int ?: def
        override fun getLong(key: String, def: Long): Long = data[key] as? Long ?: def
        override fun getFloat(key: String, def: Float): Float = data[key] as? Float ?: def
        override fun getBoolean(key: String, def: Boolean): Boolean = data[key] as? Boolean ?: def
        override fun contains(key: String): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private inner class Editor : SharedPreferences.Editor {
            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                data[key] = value; return this
            }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
                data[key] = values; return this
            }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                data[key] = value; return this
            }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                data[key] = value; return this
            }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                data[key] = value; return this
            }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                data[key] = value; return this
            }
            override fun remove(key: String): SharedPreferences.Editor {
                data.remove(key); return this
            }
            override fun clear(): SharedPreferences.Editor {
                data.clear(); return this
            }
            override fun commit(): Boolean = true
            override fun apply() {}
        }
    }

    private val now = 1_700_000_000_000L
    private lateinit var auth: OfflineAuthManager

    @Before
    fun setUp() {
        auth = OfflineAuthManager { FakePrefs() }
    }

    @Test
    fun `a stored password alone never grants offline unlock`() {
        auth.saveCredential("nurse@clinic.com", "StrongClinicPass123!")
        assertFalse(auth.isOfflineUnlockAllowed(now))
    }

    @Test
    fun `grace is 24h from the last online check and fails closed at the boundary`() {
        auth.saveCredential("nurse@clinic.com", "StrongClinicPass123!")
        auth.recordOnlineVerification(now)
        assertTrue(auth.isOfflineUnlockAllowed(now + OfflineAuthManager.OFFLINE_GRACE_MS))
        assertFalse(auth.isOfflineUnlockAllowed(now + OfflineAuthManager.OFFLINE_GRACE_MS + 1))
    }

    @Test
    fun `a rolled-back clock fails closed`() {
        auth.recordOnlineVerification(now)
        assertFalse(auth.isOfflineUnlockAllowed(now - 1_000L))
    }

    @Test
    fun `the local verifier itself still verifies while the gate is open`() {
        auth.saveCredential("nurse@clinic.com", "StrongClinicPass123!")
        auth.recordOnlineVerification(now)
        assertTrue(auth.verifyPassword("nurse@clinic.com", "StrongClinicPass123!"))
    }

    @Test
    fun `credential clear also revokes the online stamp`() {
        auth.saveCredential("nurse@clinic.com", "StrongClinicPass123!")
        auth.recordOnlineVerification(now)
        auth.clearCredential()
        assertFalse(auth.isOfflineUnlockAllowed(now))
    }
}
