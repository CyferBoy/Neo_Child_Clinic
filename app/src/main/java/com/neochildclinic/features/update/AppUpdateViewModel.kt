package com.neochildclinic.features.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.features.update.AppUpdateInfo
import com.neochildclinic.features.update.AppUpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    // regular result from a manual check). Rendered by MainActivity.
    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    // Small temporary heads-up popup shown on a *silent* (isManual = false) launch/resume
    // check when a genuine new version is found. Never shown more than once per app
    // session (see startupPopupShown below) - only ever set from
    // checkForUpdates(isManual = false).
    private val _startupPopup = MutableStateFlow<AppUpdateInfo?>(null)
    val startupPopup: StateFlow<AppUpdateInfo?> = _startupPopup.asStateFlow()

    // Session-scoped (survives config changes, resets on process death) - not persisted,
    // since "same session" means this ViewModel instance's lifetime, not across app restarts.
    private var startupPopupShown = false

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    // Generic single-OK message dialog (errors, and other non-"up to date" results).
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _installing = MutableStateFlow(false)
    val installing: StateFlow<Boolean> = _installing.asStateFlow()
    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    // Tracks the in-flight download/install coroutine so the dialog's Cancel button
    // (tapped while installing == true) can actually stop it, instead of just hiding
    // the dialog while the download kept running in the background.
    private var installJob: Job? = null

    // "App Updates / Your application is up to date." - the result of a manual check when
    // the installed version is already latest.
    private val _upToDate = MutableStateFlow(false)
    val upToDate: StateFlow<Boolean> = _upToDate.asStateFlow()

    // "Automatic update checks" toggle exposed on the App Update screen - the "Don't
    // remind me" choice on the update dialog sets this to false; a manual "Check for
    // Updates" tap always still works regardless (see AppUpdateManager.checkForUpdate).
    private val _autoChecksEnabled = MutableStateFlow(!updateManager.areAutoChecksDisabled())
    val autoChecksEnabled: StateFlow<Boolean> = _autoChecksEnabled.asStateFlow()

    fun checkForUpdates(isManual: Boolean = false) {
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            _message.value = null
            runCatching { updateManager.checkForUpdate(isManual) }
                .onSuccess { info ->
                    if (!isManual) {
                        // Silent launch/resume check: NEVER auto-opens the full Update
                        // Available dialog. A genuine new version surfaces only via the
                        // small startup popup, at most once per session. An up-to-date
                        // result is not surfaced at all here - it's only reachable
                        // through an explicit manual check (App Update screen).
                        if (info != null && !startupPopupShown) {
                            startupPopupShown = true
                            _startupPopup.value = info
                        }
                    } else {
                        _updateInfo.value = info
                        if (info == null) _upToDate.value = true
                    }
                }
                .onFailure { _message.value = it.message ?: "Unable to check for updates." }
            _checking.value = false
        }
    }

    /** Startup popup tapped - open the existing full Update Available dialog with the same
     * info, reusing AppUpdateDialog/_updateInfo exactly as a manual check does. */
    fun openUpdateFromStartupPopup() {
        val info = _startupPopup.value ?: return
        _startupPopup.value = null
        _updateInfo.value = info
    }

    /** Startup popup's auto-dismiss timer (or any other dismissal) elapsed without a tap. */
    fun dismissStartupPopup() {
        _startupPopup.value = null
    }

    fun dismissUpdate() {
        cancelInstallIfRunning()
        _updateInfo.value?.let { updateManager.dismiss(it.versionCode) }
        _updateInfo.value = null
    }

    /** "Don't remind me" on the update dialog - disables the silent startup/resume check
     * going forward. Distinct from dismissUpdate()/dismiss(versionCode), which only skips
     * this one version. Surfaced back on the App Update screen as a toggle so it isn't a
     * one-way trap. */
    fun setAutoChecksEnabled(enabled: Boolean) {
        updateManager.setAutoChecksDisabled(!enabled)
        _autoChecksEnabled.value = enabled
    }

    fun dontRemindMe() {
        setAutoChecksEnabled(false)
        dismissUpdate()
    }

    fun installUpdate() {
        val info = _updateInfo.value ?: return
        if (_installing.value) return
        installJob = viewModelScope.launch {
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

    /** Cancel button on the update dialog, tapped while a download is in progress. */
    private fun cancelInstallIfRunning() {
        if (_installing.value) {
            installJob?.cancel()
            installJob = null
            _installing.value = false
            _downloadProgress.value = DownloadProgress()
        }
    }

    fun clearMessage() { _message.value = null }

    fun dismissUpToDate() { _upToDate.value = false }
}
