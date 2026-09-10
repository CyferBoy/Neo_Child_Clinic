package com.neochildclinic.features.audit

import com.neochildclinic.data.local.entity.AuditLogEntity
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads a single patient's "Audit History" a page at a time, straight from Supabase.
 *
 * This is intentionally remote-only, the same way FullAuditLogViewModel is for the clinic-wide
 * log: the local Room audit table is never read here, so the dialog always reflects what every
 * device has actually recorded rather than whatever happened to sync down to this one. The
 * tradeoff is that it needs connectivity to show anything - callers should surface [State.error]
 * when it's set.
 *
 * Used by both PatientViewModel (patient details screen) and PatientListViewModel (patient list's
 * audit dialog), which is why this lives as a small standalone class instead of duplicated
 * per-ViewModel pagination code.
 */
class PatientAuditLogPager(
    private val postgrest: Postgrest,
    private val scope: CoroutineScope
) {
    companion object {
        private const val PAGE_SIZE = 30
    }

    data class State(
        val patientId: String? = null,
        val logs: List<AuditLogEntity> = emptyList(),
        val isLoading: Boolean = false,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var activeJob: Job? = null

    /** Starts fresh for [patientId] - call whenever the audit dialog is opened. */
    fun load(patientId: String) {
        activeJob?.cancel()
        activeJob = scope.launch {
            _state.value = State(patientId = patientId, isLoading = true)
            try {
                val firstPage = fetchPage(patientId, 0)
                _state.value = State(
                    patientId = patientId,
                    logs = firstPage,
                    isLoading = false,
                    hasMore = firstPage.size == PAGE_SIZE
                )
            } catch (e: Exception) {
                _state.value = State(
                    patientId = patientId,
                    isLoading = false,
                    error = e.message ?: "Unable to load audit history"
                )
            }
        }
    }

    /** Fetches the next page and appends it - call as the list scrolls near the bottom. */
    fun loadMore() {
        val current = _state.value
        val patientId = current.patientId ?: return
        if (current.isLoading || current.isLoadingMore || !current.hasMore) return

        activeJob = scope.launch {
            _state.value = current.copy(isLoadingMore = true, error = null)
            try {
                val page = fetchPage(patientId, current.logs.size)
                val merged = (current.logs + page).distinctBy { it.id }
                _state.value = _state.value.copy(
                    logs = merged,
                    isLoadingMore = false,
                    hasMore = page.size == PAGE_SIZE
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoadingMore = false,
                    error = e.message ?: "Unable to load more audit history"
                )
            }
        }
    }

    /** Drops any loaded pages - call when the dialog is dismissed so the next open starts clean. */
    fun clear() {
        activeJob?.cancel()
        _state.value = State()
    }

    private suspend fun fetchPage(patientId: String, offset: Int): List<AuditLogEntity> {
        val from = offset.toLong()
        val to = (offset + PAGE_SIZE - 1).toLong()

        return postgrest.from("audit_logs").select {
            filter { eq("patient_id", patientId) }
            order("timestamp", Order.DESCENDING)
            range(from, to)
        }.decodeList<AuditLogEntity>()
    }
}
