package com.neochildclinic.features.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.data.local.dao.AuditLogDao
import com.neochildclinic.data.local.entity.AuditLogEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private val ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME

private fun startOfDay(date: LocalDate): String =
    date.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime().format(ISO_FORMATTER)

private fun startOfNextDay(date: LocalDate): String =
    date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime().format(ISO_FORMATTER)

data class AuditLogUiState(
    val logs: List<AuditLogEntity> = emptyList(),
    val periodStart: LocalDate = LocalDate.now().minusDays(6),
    val periodEnd: LocalDate = LocalDate.now(),
    val selectedAction: String = "ALL",
    val selectedUser: String = "ALL",
    val availableActions: List<String> = emptyList(),
    val availableUsers: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FullAuditLogViewModel @Inject constructor(
    private val auditLogDao: AuditLogDao
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now())
    private val periodOffset = MutableStateFlow(0)
    private val selectedAction = MutableStateFlow("ALL")
    private val selectedUser = MutableStateFlow("ALL")
    private val _isRefreshing = MutableStateFlow(false)

    private val period = combine(today, periodOffset) { current, offset ->
        val start = current.minusDays(6).plusDays(offset.toLong() * 7)
        start to start.plusDays(6)
    }

    private val periodLogs = period.flatMapLatest { (start, end) ->
        auditLogDao.getLogsBetween(startOfDay(start), startOfNextDay(end))
    }

    val uiState: StateFlow<AuditLogUiState> = combine(
        period,
        periodLogs,
        selectedAction,
        selectedUser,
        _isRefreshing
    ) { (start, end), logs, action, user, refreshing ->
        val actions = logs.map { it.action.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        val users = logs.map { it.user.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        val filtered = logs.filter { log ->
            (action == "ALL" || log.action.equals(action, ignoreCase = true)) &&
            (user == "ALL" || log.user == user)
        }
        AuditLogUiState(
            logs = filtered,
            periodStart = start,
            periodEnd = end,
            selectedAction = action,
            selectedUser = user,
            availableActions = actions,
            availableUsers = users,
            isLoading = refreshing
        )
    }.catch { e ->
        emit(AuditLogUiState(error = e.message))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AuditLogUiState(isLoading = true)
    )

    fun previousPeriod() {
        periodOffset.update { it - 1 }
    }

    fun nextPeriod() {
        if (periodOffset.value < 0) periodOffset.update { it + 1 }
    }

    fun currentPeriod() {
        periodOffset.value = 0
    }

    fun setAction(action: String) {
        selectedAction.value = action
    }

    fun setUser(user: String) {
        selectedUser.value = user
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            kotlinx.coroutines.delay(250)
            _isRefreshing.value = false
        }
    }
}
