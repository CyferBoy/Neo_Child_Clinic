package com.neochildclinic.feature.patient.data
import com.neochildclinic.feature.patient.domain.repository.PatientTodoRepository
import com.neochildclinic.data.local.dao.PatientTodoDao

import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.data.local.entity.ConsultationTodoEntity
import com.neochildclinic.data.local.entity.VaccinationTodoEntity
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.data.local.entity.toEntity
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import com.neochildclinic.data.local.dao.SyncQueueDao
import com.neochildclinic.domain.model.ConsultationTodo
import com.neochildclinic.domain.model.VaccinationTodo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.neochildclinic.core.sync.cloudRefresh
import com.neochildclinic.core.sync.fetchAllPages
import com.neochildclinic.core.sync.RemotePrune
import com.neochildclinic.core.database.TransactionRunner

@Singleton
class PatientTodoRepositoryImpl @Inject constructor(
    private val dao: PatientTodoDao,
    private val transactionRunner: TransactionRunner,
    private val syncRepository: SyncRepository,
    private val syncQueueDao: SyncQueueDao,
    private val postgrest: Postgrest,
    private val sessionManager: com.neochildclinic.core.security.SessionManager
) : PatientTodoRepository {

    override suspend fun refresh() = cloudRefresh("PatientTodoRepo", rethrow = true) {
        val consultations = fetchAllPages { from, to ->
            postgrest.from("consultation_todos")
                .select {
                    filter { eq("is_deleted", false) }
                    order("id", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<ConsultationTodoEntity>()
        }
        val vaccinations = fetchAllPages { from, to ->
            postgrest.from("vaccination_todos")
                .select {
                    filter { eq("is_deleted", false) }
                    order("id", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<VaccinationTodoEntity>()
        }

        // One queue-guard read plus one local snapshot per table, then only the rows that
        // change anything are written (see [todoUpsertsForRefresh]). Previously every
        // remote row did a local lookup and a REPLACE, so even an unchanged pull issued
        // 2N local queries and rewrote the whole todos table, invalidating every flow
        // that watches it. Guard + snapshot + writes share one transaction so a local
        // edit racing the pull keeps its row (the SyncQueueDao.unsyncedIdSet rule).
        transactionRunner.run {
            val consultLocalById = dao.getAllConsultationTodos().associateBy { it.id }
            val upserted = todoUpsertsForRefresh(
                remote = consultations,
                localById = consultLocalById,
                unsyncedIdSet = syncQueueDao.unsyncedIdSet("CONSULTATION_TODO"),
                id = { it.id },
                isSynced = { it.isSynced },
                markedSynced = { it.copy(isSynced = true) }
            )
            upserted.forEach { dao.insertConsultation(it) }
            // Remote-deletion propagation (NCC-023 / DA-01): synced todo rows the remote set
            // no longer lists and no queue entry claims are drift (todos are leaf rows).
            RemotePrune.computeBatches(
                localIds = consultLocalById.keys.toList(),
                syncedIds = consultLocalById.values.filter { it.isSynced }.mapTo(HashSet()) { it.id },
                remoteIds = consultations.mapTo(HashSet()) { it.id },
                pendingIds = syncQueueDao.unsyncedIdSet("CONSULTATION_TODO")
            ).flatten().forEach { dao.deleteConsultation(it) }
        }
        transactionRunner.run {
            val vaccLocalById = dao.getAllVaccinationTodos().associateBy { it.id }
            val upserted = todoUpsertsForRefresh(
                remote = vaccinations,
                localById = vaccLocalById,
                unsyncedIdSet = syncQueueDao.unsyncedIdSet("VACCINATION_TODO"),
                id = { it.id },
                isSynced = { it.isSynced },
                markedSynced = { it.copy(isSynced = true) }
            )
            upserted.forEach { dao.insertVaccination(it) }
            RemotePrune.computeBatches(
                localIds = vaccLocalById.keys.toList(),
                syncedIds = vaccLocalById.values.filter { it.isSynced }.mapTo(HashSet()) { it.id },
                remoteIds = vaccinations.mapTo(HashSet()) { it.id },
                pendingIds = syncQueueDao.unsyncedIdSet("VACCINATION_TODO")
            ).flatten().forEach { dao.deleteVaccination(it) }
        }
    }

    override fun getConsultationsByDateAndStatus(date: String, status: String): Flow<List<ConsultationTodo>> = 
        dao.getConsultationsByDateAndStatus(date, status).map { entities: List<ConsultationTodoEntity> -> entities.map { it.toDomain() } }
    override fun getVaccinationsByDateAndStatus(date: String, status: String): Flow<List<VaccinationTodo>> = 
        dao.getVaccinationsByDateAndStatus(date, status).map { entities: List<VaccinationTodoEntity> -> entities.map { it.toDomain() } }
    override fun getDatesWithData(start: String, end: String): Flow<List<String>> = dao.getDatesWithData(start, end)

    override suspend fun updateStatus(type: String, id: String, status: String) {
        val now = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp()
        if (type == "CONSULTATION_TODO") {
            dao.updateConsultationStatus(id, status, now)
        } else {
            dao.updateVaccinationStatus(id, status, now)
        }
        syncRepository.enqueue(type, id, com.neochildclinic.domain.model.SyncOperation.UPDATE, com.neochildclinic.domain.model.SyncPriority.MEDIUM)
    }

    override suspend fun addConsultation(todo: ConsultationTodo) {
        val entity = todo.toEntity()
        dao.insertConsultation(entity)
        syncRepository.enqueue("CONSULTATION_TODO", entity.id, SyncOperation.CREATE, SyncPriority.MEDIUM)
    }

    override suspend fun addVaccination(todo: VaccinationTodo) {
        val entity = todo.toEntity()
        dao.insertVaccination(entity)
        syncRepository.enqueue("VACCINATION_TODO", entity.id, SyncOperation.CREATE, SyncPriority.MEDIUM)
    }

    override suspend fun deleteConsultation(id: String) {
        val userName = sessionManager.getCurrentUserName()
        val now = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp()
dao.deleteConsultation(id)
          syncRepository.enqueue("CONSULTATION_TODO", id, SyncOperation.DELETE, SyncPriority.MEDIUM)
    }

    override suspend fun deleteVaccination(id: String) {
        val userName = sessionManager.getCurrentUserName()
        val now = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp()
dao.deleteVaccination(id)
          syncRepository.enqueue("VACCINATION_TODO", id, SyncOperation.DELETE, SyncPriority.MEDIUM)
    }
}

/** Decision core of the todos refresh: which remote rows to write now. Keeps the per-row
 * guard rules exactly - a queue-unsynced row and a dirty local row are never overwritten;
 * a synced row whose content already equals the remote copy is not rewritten (REPLACE of
 * identical bytes would invalidate every flow watching the table without changing state). */
internal fun <T> todoUpsertsForRefresh(
    remote: List<T>,
    localById: Map<String, T>,
    unsyncedIdSet: Set<String>,
    id: (T) -> String,
    isSynced: (T) -> Boolean,
    markedSynced: (T) -> T
): List<T> = remote.filter { remoteRow ->
    val local = localById[id(remoteRow)]
    (local == null || isSynced(local)) &&
        id(remoteRow) !in unsyncedIdSet &&
        markedSynced(remoteRow) != local
}
