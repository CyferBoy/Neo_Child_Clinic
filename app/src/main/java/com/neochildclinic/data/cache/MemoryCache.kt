package com.neochildclinic.data.cache

import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.Vaccination
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-wide L1 in-memory cache.
 *
 * Non-persistent and process-scoped. Room remains the persistent/offline
 * source of truth and Supabase remains the cloud source of truth.
 */
@Singleton
class MemoryCache @Inject constructor() {

    private data class Entry<T>(val value: T, val expiresAtMs: Long)

    private val patients = ConcurrentHashMap<String, Entry<Patient?>>()
    private val vaccinations = ConcurrentHashMap<String, Entry<Vaccination?>>()
    private val profiles = ConcurrentHashMap<String, Entry<Profile?>>()
    private val batches = ConcurrentHashMap<String, Entry<VaccineBatchEntity?>>()

    private val ttlMs = 5 * 60 * 1000L

    fun getPatient(id: String): Patient? = get(patients, id)

    fun putPatient(patient: Patient) {
        patients[patient.id] = Entry(patient, expiresAt())
    }

    fun invalidatePatient(id: String) {
        patients.remove(id)
    }

    fun getVaccination(id: String): Vaccination? = get(vaccinations, id)

    fun putVaccination(vaccination: Vaccination) {
        vaccinations[vaccination.id] = Entry(vaccination, expiresAt())
    }

    fun invalidateVaccination(id: String) {
        vaccinations.remove(id)
    }

    fun getProfile(id: String): Profile? = get(profiles, id)

    fun putProfile(profile: Profile) {
        profiles[profile.id] = Entry(profile, expiresAt())
    }

    fun invalidateProfile(id: String) {
        profiles.remove(id)
    }

    fun getBatch(id: String): VaccineBatchEntity? = get(batches, id)

    fun putBatch(batch: VaccineBatchEntity) {
        batches[batch.batchId] = Entry(batch, expiresAt())
    }

    fun invalidateBatch(id: String) {
        batches.remove(id)
    }

    fun clearPatients() {
        patients.clear()
    }

    /**
     * Clears every L1 entry. Must be called when the authenticated user changes
     * so data from a previous staff session cannot be served to the next user.
     */
    fun clearAll() {
        patients.clear()
        vaccinations.clear()
        profiles.clear()
        batches.clear()
    }

    /**
     * Optional maintenance sweep. Normal reads also lazily remove expired
     * entries, so a periodic job is not required for correctness.
     */
    fun clearExpired() {
        val now = System.currentTimeMillis()
        patients.removeExpired(now)
        vaccinations.removeExpired(now)
        profiles.removeExpired(now)
        batches.removeExpired(now)
    }

    private fun expiresAt(): Long = System.currentTimeMillis() + ttlMs

    private fun <T> get(map: ConcurrentHashMap<String, Entry<T>>, key: String): T? {
        val entry = map[key] ?: return null
        if (entry.expiresAtMs <= System.currentTimeMillis()) {
            map.remove(key, entry)
            return null
        }
        return entry.value
    }

    private fun <T> ConcurrentHashMap<String, Entry<T>>.removeExpired(now: Long) {
        entries.forEach { (key, entry) ->
            if (entry.expiresAtMs <= now) remove(key, entry)
        }
    }
}
