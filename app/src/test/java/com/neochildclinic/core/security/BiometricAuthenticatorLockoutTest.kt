package com.neochildclinic.core.security

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the biometric lockout state machine through its public API only.
 */
class BiometricAuthenticatorLockoutTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var fakeContext: Context

    class FakeSharedPreferences : SharedPreferences {
        val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(data)
        override fun getString(key: String?, defValue: String?): String? = (data[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (data[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        inner class Editor : SharedPreferences.Editor {
            private val modifications = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor { key?.let { modifications[it] = value }; return this }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { key?.let { modifications[it] = values }; return this }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor { key?.let { modifications[it] = value }; return this }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor { key?.let { modifications[it] = value }; return this }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { key?.let { modifications[it] = value }; return this }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { key?.let { modifications[it] = value }; return this }
            override fun remove(key: String?): SharedPreferences.Editor { key?.let { removals.add(it) }; return this }
            override fun clear(): SharedPreferences.Editor { clear = true; return this }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clear) data.clear()
                removals.forEach { data.remove(it) }
                modifications.forEach { (k, v) -> data[k] = v }
            }
        }
    }

    class FakeContext(private val prefs: SharedPreferences) : ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        override fun getApplicationContext(): Context = this
    }

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        fakeContext = FakeContext(fakePrefs)

        BiometricLockManager.clearLockSuppression()
        BiometricLockManager.setProtectionEnabled(true)
        BiometricLockManager.resetNeedsAuthForTest()
        BiometricLockManager.resetLockStateResolvedForTest()
    }

    @Test
    fun initialLockoutStateIsFalse() {
        assertFalse("Initially biometrics must not be locked out", BiometricAuthenticator.isBiometricLockedOut(fakeContext))
    }

    @Test
    fun resetBiometricStateDoesNotClearLockout() {
        fakePrefs.edit()
            .putBoolean("is_biometric_locked_out", true)
            .putString("iv", "someIv")
            .apply()

        BiometricAuthenticator.resetBiometricState(fakeContext)

        assertTrue(
            "is_biometric_locked_out must NOT be wiped by resetBiometricState",
            BiometricAuthenticator.isBiometricLockedOut(fakeContext)
        )
        assertNull("iv must be removed", fakePrefs.getString("iv", null))
    }

    @Test
    fun passwordAuthenticationClearsBiometricLockoutAndUnlocksApp() {
        fakePrefs.edit().putBoolean("is_biometric_locked_out", true).apply()
        BiometricLockManager.lock()
        assertTrue(BiometricAuthenticator.isBiometricLockedOut(fakeContext))
        assertTrue(BiometricLockManager.isAppLocked.value)

        // MainActivity.authenticateWithAccountPassword success path:
        BiometricAuthenticator.clearBiometricLockout(fakeContext)
        BiometricLockManager.unlockAfterKeystoreVerification()

        assertFalse("Lockout must be cleared", BiometricAuthenticator.isBiometricLockedOut(fakeContext))
        assertFalse("App must be unlocked after password verification", BiometricLockManager.isAppLocked.value)
        assertFalse("needsAuth must be false", BiometricLockManager.needsAuth.value)
    }
}
