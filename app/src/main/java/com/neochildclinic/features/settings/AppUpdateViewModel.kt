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
    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _installing = MutableStateFlow(false)
    val installing: StateFlow<Boolean> = _installing.asStateFlow()
    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    fun checkForUpdates(isManual: Boolean = false) {
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            _message.value = null
            runCatching { updateManager.checkForUpdate() }
                .onSuccess { info ->
                    if (info?.updateType == UpdateType.REUPDATE && !isManual) {
                        // Silent on startup if already latest
                        _updateInfo.value = null
                    } else {
                        _updateInfo.value = info
                    }
                    if (isManual && info == null) _message.value = "Your application is up to date."
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
}
