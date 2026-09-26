package com.neochildclinic.data.repository
import com.neochildclinic.domain.repository.DoctorAvailabilityRepository

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
import com.neochildclinic.data.repository.SyncRepositoryImpl
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoctorAvailabilityRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val postgrest: Postgrest,
    private val syncRepository: SyncRepositoryImpl,
    private val auditLogger: com.neochildclinic.core.logger.AuditLogger
) : DoctorAvailabilityRepository {

    private val dao = database.doctorAvailabilityDao()

    fun getWeeklySlots(doctorId: String): Flow<List<DoctorWeeklySlot>> =
        dao.getActiveWeeklySlotsForDoctor(doctorId)

    fun getExceptions(doctorId: String): Flow<List<DoctorSlotException>> =
        dao.getExceptionsForDoctor(doctorId).map { list -> list.map { it.toDomain() } }

    override suspend fun getActiveWeeklySlotsForDay(doctorId: String, dayOfWeek: Int): List<DoctorWeeklySlot> =
        dao.getActiveWeeklySlotsForDay(doctorId, dayOfWeek)

    override suspend fun getExceptionsForDate(doctorId: String, date: String): List<DoctorSlotException> =
        dao.getExceptionsForDate(doctorId, date).map { it.toDomain() }

    suspend fun getWeeklySlotById(id: String): DoctorWeeklySlot? =
        dao.getWeeklySlotById(id)

    suspend fun addWeeklySlot(
        doctorId: String,
        dayOfWeek: Int,
        startMinute: Int,
        endMinute: Int,
        actor: String?
    ) {
        val now = PatientUtils.getCurrentIsoTimestamp()
        val existing = dao.findWeeklySlot(doctorId, dayOfWeek, startMinute, endMinute)

        if (existing != null) {
            if (!existing.isActive) {
                dao.setWeeklySlotActive(existing.id, true, now, actor)
                syncRepository.enqueue("DOCTOR_WEEKLY_SLOT", existing.id, SyncOperation.UPDATE, SyncPriority.LOW)
                auditLogger.log(
                    module = "DOCTOR_TIMINGS",
                    entityType = "WEEKLY_SLOT",
                    entityId = existing.id,
                    action = "SLOT_REACTIVATED",
                    remarks = "Day $dayOfWeek, ${com.neochildclinic.domain.model.TimeRange.formatMinuteOfDay(startMinute)} - ${com.neochildclinic.domain.model.TimeRange.formatMinuteOfDay(endMinute)}"
                )
            }
        } else {
            val entity = DoctorWeeklySlotEntity(
                id = java.util.UUID.randomUUID().toString(),
                doctorId = doctorId,
                dayOfWeek = dayOfWeek,
                startMinute = startMinute,
                endMinute = endMinute,
                isActive = true,
                createdAt = now,
                updatedAt = now,
                isSynced = false,
                createdBy = actor,
                updatedBy = actor
            )
            dao.upsertWeeklySlot(entity)
            syncRepository.enqueue("DOCTOR_WEEKLY_SLOT", entity.id, SyncOperation.CREATE, SyncPriority.LOW)
            auditLogger.log(
                module = "DOCTOR_TIMINGS",
                entityType = "WEEKLY_SLOT",
                entityId = entity.id,
                action = "SLOT_CREATED",
                remarks = "Day $dayOfWeek, ${com.neochildclinic.domain.model.TimeRange.formatMinuteOfDay(startMinute)} - ${com.neochildclinic.domain.model.TimeRange.formatMinuteOfDay(endMinute)}"
            )
        }
    }

    suspend fun removeWeeklySlot(slotId: String, actor: String?) {
        val now = PatientUtils.getCurrentIsoTimestamp()
        dao.setWeeklySlotActive(slotId, false, now, actor)
        syncRepository.enqueue("DOCTOR_WEEKLY_SLOT", slotId, SyncOperation.UPDATE, SyncPriority.LOW)
        auditLogger.log(
            module = "DOCTOR_TIMINGS",
            entityType = "WEEKLY_SLOT",
            entityId = slotId,
            action = "SLOT_DELETED"
        )
    }

    suspend fun addException(exception: DoctorSlotException, actor: String?) {
        val now = PatientUtils.getCurrentIsoTimestamp()
        val entity = exception.copy(
            createdAt = exception.createdAt.ifBlank { now },
            updatedAt = now,
            createdBy = exception.createdBy ?: actor,
            updatedBy = actor
        ).toEntity(isSynced = false)
        dao.upsertException(entity)
        syncRepository.enqueue("DOCTOR_SLOT_EXCEPTION", entity.id, SyncOperation.CREATE, SyncPriority.LOW)
        val timeDesc = if (exception.startMinute != null && exception.endMinute != null) {
            "${com.neochildclinic.domain.model.TimeRange.formatMinuteOfDay(exception.startMinute)} - ${com.neochildclinic.domain.model.TimeRange.formatMinuteOfDay(exception.endMinute)}"
        } else null
        auditLogger.log(
            module = "DOCTOR_TIMINGS",
            entityType = "UNAVAILABILITY",
            entityId = entity.id,
            action = "EXCEPTION_CREATED",
            remarks = "${exception.exceptionDate}${timeDesc?.let { ", $it" } ?: ""}"
        )
    }

    suspend fun deleteException(id: String, actor: String?) {
        val now = PatientUtils.getCurrentIsoTimestamp()
        dao.deleteException(id, now, actor)
        syncRepository.enqueue("DOCTOR_SLOT_EXCEPTION", id, SyncOperation.UPDATE, SyncPriority.LOW)
        auditLogger.log(
            module = "DOCTOR_TIMINGS",
            entityType = "UNAVAILABILITY",
            entityId = id,
            action = "EXCEPTION_SOFT_DELETED"
        )
    }

    override suspend fun refresh() = cloudRefresh("DoctorAvailabilityRepo") {
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
    }
}
