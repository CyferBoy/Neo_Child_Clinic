package com.neochildclinic.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.update.AppUpdateInfo
import com.neochildclinic.core.update.AppUpdateManager
import com.neochildclinic.core.update.UpdateType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadProgress(
    val percent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L
)

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val updateManager: AppUpdateManager
) : ViewModel() {
    // App-wide "new version available" nag (silent auto-check on launch/resume, or a
    // regular UPDATE result from a manual check). Rendered by MainActivity.
    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    // Generic single-OK message dialog (errors, and other non-"up to date" results).
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _installing = MutableStateFlow(false)
    val installing: StateFlow<Boolean> = _installing.asStateFlow()
    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    // "App Updates / Your application is up to date." with Re-update/Downgrade/OK - the
    // mandatory first result of a manual check when the installed version is already latest.
    private val _upToDate = MutableStateFlow(false)
    val upToDate: StateFlow<Boolean> = _upToDate.asStateFlow()

    // Re-update Available (result of tapping Re-update - a fresh, dedicated check for the
    // installed version's own release, never auto-shown from the initial check).
    private val _reupdateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val reupdateInfo: StateFlow<AppUpdateInfo?> = _reupdateInfo.asStateFlow()

    // Downgrade: version list -> selected version confirmation.
    private val _downgradeVersions = MutableStateFlow<List<AppUpdateInfo>?>(null)
    val downgradeVersions: StateFlow<List<AppUpdateInfo>?> = _downgradeVersions.asStateFlow()
    private val _selectedDowngrade = MutableStateFlow<AppUpdateInfo?>(null)
    val selectedDowngrade: StateFlow<AppUpdateInfo?> = _selectedDowngrade.asStateFlow()
    // Tracks the last-picked version separately from _selectedDowngrade so the list can
    // keep it highlighted after "Change Version" (which clears _selectedDowngrade to close
    // the confirmation dialog, but shouldn't lose the highlight on the list underneath it).
    private val _highlightedVersionCode = MutableStateFlow<Long?>(null)
    val highlightedVersionCode: StateFlow<Long?> = _highlightedVersionCode.asStateFlow()
    private val _noDowngradeAvailable = MutableStateFlow(false)
    val noDowngradeAvailable: StateFlow<Boolean> = _noDowngradeAvailable.asStateFlow()

    fun checkForUpdates(isManual: Boolean = false) {
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            _message.value = null
            runCatching { updateManager.checkForUpdate() }
                .onSuccess { info ->
                    if (info?.updateType == UpdateType.REUPDATE) {
                        // Same version as the latest release - never auto-jump straight to
                        // "Re-update Available", on a manual OR a silent check. The first
                        // result of a manual check must be "up to date"; Re-update is only
                        // reachable from there via its own explicit button/fresh check.
                        _updateInfo.value = null
                        if (isManual) _upToDate.value = true
                    } else {
                        _updateInfo.value = info
                        if (isManual && info == null) _upToDate.value = true
                    }
                }
                .onFailure { _message.value = it.message ?: "Unable to check for updates." }
            _checking.value = false
        }
    }

    fun dismissUpdate() {
        _updateInfo.value?.let { updateManager.dismiss(it.versionCode) }
        _updateInfo.value = null
    }

    fun installUpdate() {
        val info = _updateInfo.value ?: return
        if (_installing.value) return
        viewModelScope.launch {
            _installing.value = true
            _downloadProgress.value = DownloadProgress()
            updateManager.downloadAndInstall(info) { percent, downloaded, total ->
                _downloadProgress.value = DownloadProgress(percent, downloaded, total)
            }.onFailure {
                _message.value = it.message ?: "Unable to install the update."
            }
            _installing.value = false
        }
    }

    fun clearMessage() { _message.value = null }

    fun dismissUpToDate() { _upToDate.value = false }

    /** Re-update button on the "up to date" dialog: a fresh check for the installed release. */
    fun reupdate() {
        _upToDate.value = false
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            runCatching { updateManager.checkForReupdate() }
                .onSuccess { info ->
                    if (info != null) _reupdateInfo.value = info
                    else _message.value = "No re-installable release is currently available for this version."
                }
                .onFailure { _message.value = it.message ?: "Unable to check for updates." }
            _checking.value = false
        }
    }

    fun dismissReupdate() { _reupdateInfo.value = null }

    fun installReupdate() {
        val info = _reupdateInfo.value ?: return
        if (_installing.value) return
        viewModelScope.launch {
            _installing.value = true
            _downloadProgress.value = DownloadProgress()
            updateManager.downloadAndInstall(info) { percent, downloaded, total ->
                _downloadProgress.value = DownloadProgress(percent, downloaded, total)
            }.onFailure {
                _message.value = it.message ?: "Unable to install the update."
            }
            _installing.value = false
            _reupdateInfo.value = null
        }
    }

    /** Downgrade button on the "up to date" dialog: loads the version list. */
    fun startDowngrade() {
        _upToDate.value = false
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            runCatching { updateManager.listDowngradeVersions() }
                .onSuccess { versions ->
                    if (versions.isEmpty()) _noDowngradeAvailable.value = true
                    else _downgradeVersions.value = versions
                }
                .onFailure { _message.value = it.message ?: "Unable to load previous versions." }
            _checking.value = false
        }
    }

    fun dismissNoDowngradeAvailable() { _noDowngradeAvailable.value = false }

    /** List's Cancel button - closes the whole downgrade flow without selecting anything. */
    fun cancelDowngradeList() {
        _downgradeVersions.value = null
        _selectedDowngrade.value = null
        _highlightedVersionCode.value = null
    }

    fun selectDowngradeVersion(info: AppUpdateInfo) {
        _selectedDowngrade.value = info
        _highlightedVersionCode.value = info.versionCode
    }

    /** Confirmation dialog's Change Version button - back to the list, keeping it populated. */
    fun changeDowngradeVersion() { _selectedDowngrade.value = null }

    /** Confirmation dialog's Cancel button - abandons the whole flow. */
    fun cancelDowngradeConfirm() {
        _selectedDowngrade.value = null
        _downgradeVersions.value = null
        _highlightedVersionCode.value = null
    }

    /** Confirmation dialog's Downgrade button - the only place actual install begins. */
    fun confirmDowngrade() {
        val info = _selectedDowngrade.value ?: return
        if (_installing.value) return
        viewModelScope.launch {
            _installing.value = true
            _downloadProgress.value = DownloadProgress()
            updateManager.downloadAndInstall(info) { percent, downloaded, total ->
                _downloadProgress.value = DownloadProgress(percent, downloaded, total)
            }.onFailure {
                _message.value = it.message ?: "Unable to install the update."
            }
            _installing.value = false
            _selectedDowngrade.value = null
            _downgradeVersions.value = null
            _highlightedVersionCode.value = null
        }
    }
}
