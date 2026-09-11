package com.neochildclinic.data.repository

import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.DoctorSlotExceptionEntity
import com.neochildclinic.data.local.entity.DoctorWeeklySlotEntity
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.data.local.entity.toEntity
import com.neochildclinic.domain.model.DoctorSlotException
import com.neochildclinic.domain.model.DoctorWeeklySlot
import com.neochildclinic.domain.model.TimeRange
import com.neochildclinic.domain.repository.DoctorAvailabilityRepository
import com.neochildclinic.domain.repository.SyncRepository
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoctorAvailabilityRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val postgrest: Postgrest,
    private val syncRepository: SyncRepository
) : DoctorAvailabilityRepository {

    private val dao = database.doctorAvailabilityDao()

    override fun getWeeklySlots(doctorId: String): Flow<List<DoctorWeeklySlot>> =
        dao.getActiveWeeklySlotsForDoctor(doctorId).map { list -> list.map { it.toDomain() } }

    override fun getExceptions(doctorId: String): Flow<List<DoctorSlotException>> =
        dao.getExceptionsForDoctor(doctorId).map { list -> list.map { it.toDomain() } }

    override suspend fun getActiveWeeklySlotsForDay(doctorId: String, dayOfWeek: Int): List<DoctorWeeklySlot> =
        dao.getActiveWeeklySlotsForDay(doctorId, dayOfWeek).map { it.toDomain() }

    override suspend fun getExceptionsForDate(doctorId: String, date: String): List<DoctorSlotException> =
        dao.getExceptionsForDate(doctorId, date).map { it.toDomain() }

    override suspend fun getWeeklySlotById(id: String): DoctorWeeklySlot? =
        dao.getWeeklySlotById(id)?.toDomain()

    override suspend fun setWeeklySlotEnabled(
        doctorId: String,
        dayOfWeek: Int,
        range: TimeRange,
        enabled: Boolean,
        actor: String?
    ) {
        val now = PatientUtils.getCurrentIsoTimestamp()
        val existing = dao.findWeeklySlot(doctorId, dayOfWeek, range.startMinute, range.endMinute)

        if (enabled) {
            if (existing == null) {
                val entity = DoctorWeeklySlotEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    doctorId = doctorId,
                    dayOfWeek = dayOfWeek,
                    startMinute = range.startMinute,
                    endMinute = range.endMinute,
                    isActive = true,
                    createdAt = now,
                    updatedAt = now,
                    isSynced = false,
                    createdBy = actor,
                    updatedBy = actor
                )
                dao.upsertWeeklySlot(entity)
                syncRepository.enqueue("DOCTOR_WEEKLY_SLOT", entity.id, SyncOperation.CREATE, SyncPriority.LOW)
            } else if (!existing.isActive) {
                dao.setWeeklySlotActive(existing.id, true, now, actor)
                syncRepository.enqueue("DOCTOR_WEEKLY_SLOT", existing.id, SyncOperation.UPDATE, SyncPriority.LOW)
            }
        } else {
            // Soft-delete only (project convention): deactivate rather than physically
            // remove, so any consultation/vaccination that already references this slot's
            // id via availabilitySlotId can still resolve it for display.
            if (existing != null && existing.isActive) {
                dao.setWeeklySlotActive(existing.id, false, now, actor)
                syncRepository.enqueue("DOCTOR_WEEKLY_SLOT", existing.id, SyncOperation.UPDATE, SyncPriority.LOW)
            }
        }
    }

    override suspend fun addException(exception: DoctorSlotException, actor: String?) {
        val now = PatientUtils.getCurrentIsoTimestamp()
        val entity = exception.copy(
            createdAt = exception.createdAt.ifBlank { now },
            updatedAt = now,
            createdBy = exception.createdBy ?: actor,
            updatedBy = actor
        ).toEntity(isSynced = false, isDeleted = false)
        dao.upsertException(entity)
        syncRepository.enqueue("DOCTOR_SLOT_EXCEPTION", entity.id, SyncOperation.CREATE, SyncPriority.LOW)
    }

    override suspend fun deleteException(id: String, actor: String?) {
        val now = PatientUtils.getCurrentIsoTimestamp()
        dao.markExceptionDeleted(id, now, actor)
        syncRepository.enqueue("DOCTOR_SLOT_EXCEPTION", id, SyncOperation.UPDATE, SyncPriority.LOW)
    }

    override suspend fun refresh() {
        try {
            val remoteSlots = postgrest.from("doctor_weekly_slots").select().decodeList<DoctorWeeklySlotEntity>()
            remoteSlots.forEach { remote ->
                val local = dao.getWeeklySlotById(remote.id)
                if (local == null || local.isSynced) dao.upsertWeeklySlot(remote.copy(isSynced = true))
            }

            val remoteExceptions = postgrest.from("doctor_slot_exceptions").select().decodeList<DoctorSlotExceptionEntity>()
            remoteExceptions.forEach { remote ->
                val local = dao.getExceptionById(remote.id)
                if (local == null || local.isSynced) dao.upsertException(remote.copy(isSynced = true))
            }
        } catch (e: Exception) {
            // Same "don't let one repository's refresh failure abort the whole
            // RefreshDataUseCase chain" defensiveness as ExpenseRepositoryImpl - this
            // table has no FK dependents in this refresh sequence, so a stale/missing
            // local copy just means slightly outdated availability until the next sync.
            android.util.Log.e("DoctorAvailabilityRepo", "Refresh failed", e)
        }
    }
}
