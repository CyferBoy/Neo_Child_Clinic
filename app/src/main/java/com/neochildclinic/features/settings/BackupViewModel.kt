package com.neochildclinic.features.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.domain.model.*
import com.neochildclinic.domain.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val isBusy: Boolean = false,
    val progressLabel: String? = null,
    val history: List<BackupHistory> = emptyList(),
    val autoBackupSettings: AutoBackupSettings = AutoBackupSettings(),
    val cloudConfigured: Boolean = false,
    val cloudBackups: List<CloudBackupMetadata> = emptyList(),
    val cloudBackupsLoading: Boolean = false,
    val cloudBackupsError: String? = null,
    val pendingRestoreSummary: RestoreSummary? = null,
    val pendingRestoreSource: RestoreSource? = null,
    val hasSafetyBackup: Boolean = false,
    val message: BackupUiMessage? = null
)

sealed class RestoreSource {
    data class Local(val uri: Uri, val password: CharArray) : RestoreSource()
    data class Cloud(val backupId: String, val password: CharArray) : RestoreSource()

    /** Zeroes the held password. Safe to call more than once. */
    fun clearPassword() {
        when (this) {
            is Local -> password.fill('\u0000')
            is Cloud -> password.fill('\u0000')
        }
    }
}

data class BackupUiMessage(val text: String, val isError: Boolean)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            backupRepository.observeHistory().collect { history ->
                _uiState.value = _uiState.value.copy(history = history.map { it.toDomain() })
            }
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                autoBackupSettings = backupRepository.getAutoBackupSettings(),
                cloudConfigured = backupRepository.isCloudConfigured(),
                hasSafetyBackup = backupRepository.hasSafetyBackup()
            )
            if (_uiState.value.cloudConfigured) refreshCloudBackups()
        }
    }

    private fun setBusy(label: String?) {
        _uiState.value = _uiState.value.copy(isBusy = label != null, progressLabel = label)
    }

    private fun onProgress(progress: BackupProgress) {
        val label = when (progress) {
            is BackupProgress.Stage -> progress.label
            is BackupProgress.TableDone -> "${progress.tableLabel} done"
            BackupProgress.Done -> null
        }
        if (label != null) _uiState.value = _uiState.value.copy(progressLabel = label)
    }

    private fun showResult(result: BackupOperationResult, successText: String) {
        _uiState.value = _uiState.value.copy(
            isBusy = false,
            progressLabel = null,
            message = if (result.success) BackupUiMessage(successText, false)
            else BackupUiMessage(result.userMessage ?: "Backup failed.\nPlease try again.", true)
        )
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun dismissRestoreSummary() {
        // The pending source's password was never consumed if the user backs out here
        // (confirmRestore() is what normally triggers the repository to clear it) - clear
        // it ourselves so an abandoned restore doesn't leave a live password in memory.
        _uiState.value.pendingRestoreSource?.clearPassword()
        _uiState.value = _uiState.value.copy(pendingRestoreSummary = null, pendingRestoreSource = null)
    }

    // ---- Local export ----

    fun exportBackup(uri: Uri, password: CharArray) = viewModelScope.launch {
        setBusy("Creating backup...")
        val result = backupRepository.exportBackupToUri(uri, password) { onProgress(it) }
        showResult(result, "Backup saved successfully.")
    }

    // ---- Local import ----

    /** Step 1: decrypt+validate only, show a restore summary before touching any data. */
    fun peekLocalBackup(uri: Uri, password: CharArray) = viewModelScope.launch {
        setBusy("Checking backup...")
        // peekBackupFromUri() clears whatever array it's given once it's done with it, but
        // the same password is needed again in confirmRestore() below if the user goes
        // ahead - so peek gets its own copy, and the original survives in RestoreSource
        // until the terminal restore call (or dismissRestoreSummary()) clears it.
        when (val validation = backupRepository.peekBackupFromUri(uri, password.copyOf())) {
            is BackupValidationResult.Valid -> _uiState.value = _uiState.value.copy(
                isBusy = false, progressLabel = null,
                pendingRestoreSummary = validation.summary,
                pendingRestoreSource = RestoreSource.Local(uri, password)
            )
            is BackupValidationResult.Invalid -> {
                password.fill('\u0000')
                _uiState.value = _uiState.value.copy(
                    isBusy = false, progressLabel = null,
                    message = BackupUiMessage(validation.message, true)
                )
            }
        }
    }

    /** Step 2: user picked Replace or Merge on the summary dialog. */
    fun confirmRestore(mode: RestoreMode) = viewModelScope.launch {
        val source = _uiState.value.pendingRestoreSource ?: return@launch
        // Clear pendingRestoreSource from state (not the password itself - the repository
        // call below still needs it and will clear it once the restore completes).
        _uiState.value = _uiState.value.copy(pendingRestoreSummary = null, pendingRestoreSource = null)
        setBusy("Preparing restore...")
        val result = when (source) {
            is RestoreSource.Local -> backupRepository.restoreBackupFromUri(source.uri, source.password, mode) { onProgress(it) }
            is RestoreSource.Cloud -> backupRepository.cloudRestore(source.backupId, source.password, mode) { onProgress(it) }
        }
        _uiState.value = _uiState.value.copy(hasSafetyBackup = backupRepository.hasSafetyBackup())
        showResult(result, "Restore completed successfully.")
    }

    // ---- Cloud ----

    fun refreshCloudBackups() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(cloudBackupsLoading = true, cloudBackupsError = null)
        backupRepository.listCloudBackups().fold(
            onSuccess = { list -> _uiState.value = _uiState.value.copy(cloudBackups = list, cloudBackupsLoading = false) },
            onFailure = { e -> _uiState.value = _uiState.value.copy(cloudBackupsLoading = false, cloudBackupsError = e.message) }
        )
    }

    fun cloudBackupNow(password: CharArray) = viewModelScope.launch {
        setBusy("Creating backup...")
        val result = backupRepository.cloudBackupNow(password) { onProgress(it) }
        showResult(result, "Cloud backup completed successfully.")
        if (result.success) refreshCloudBackups()
    }

    fun peekCloudBackup(backupId: String, password: CharArray) = viewModelScope.launch {
        setBusy("Downloading backup...")
        // Same copy-for-peek reasoning as peekLocalBackup() above.
        when (val validation = backupRepository.peekCloudBackup(backupId, password.copyOf())) {
            is BackupValidationResult.Valid -> _uiState.value = _uiState.value.copy(
                isBusy = false, progressLabel = null,
                pendingRestoreSummary = validation.summary,
                pendingRestoreSource = RestoreSource.Cloud(backupId, password)
            )
            is BackupValidationResult.Invalid -> {
                password.fill('\u0000')
                _uiState.value = _uiState.value.copy(
                    isBusy = false, progressLabel = null,
                    message = BackupUiMessage(validation.message, true)
                )
            }
        }
    }

    fun deleteCloudBackup(backupId: String) = viewModelScope.launch {
        setBusy("Deleting...")
        backupRepository.deleteCloudBackup(backupId).fold(
            onSuccess = {
                _uiState.value = _uiState.value.copy(isBusy = false, progressLabel = null, message = BackupUiMessage("Backup deleted.", false))
                refreshCloudBackups()
            },
            onFailure = { e ->
                _uiState.value = _uiState.value.copy(isBusy = false, progressLabel = null, message = BackupUiMessage(e.message ?: "Delete failed.", true))
            }
        )
    }

    // ---- Automatic backup ----

    fun enableAutomaticBackup(password: CharArray, settings: AutoBackupSettings) = viewModelScope.launch {
        backupRepository.enableAutomaticBackup(password, settings)
        _uiState.value = _uiState.value.copy(autoBackupSettings = backupRepository.getAutoBackupSettings())
    }

    fun updateAutomaticBackupSettings(settings: AutoBackupSettings) = viewModelScope.launch {
        backupRepository.updateAutomaticBackupSettings(settings)
        _uiState.value = _uiState.value.copy(autoBackupSettings = backupRepository.getAutoBackupSettings())
    }

    fun disableAutomaticBackup() = viewModelScope.launch {
        backupRepository.disableAutomaticBackup()
        _uiState.value = _uiState.value.copy(autoBackupSettings = backupRepository.getAutoBackupSettings())
    }

    // ---- Safety ----

    fun restoreSafetyBackup() = viewModelScope.launch {
        setBusy("Restoring previous data...")
        val result = backupRepository.restoreSafetyBackup { onProgress(it) }
        showResult(result, "Previous data restored.")
    }
}
