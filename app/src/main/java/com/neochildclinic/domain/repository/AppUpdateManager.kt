package com.neochildclinic.domain.repository

/**
 * The update-manager operations used by AppUpdateViewModel. [T] is the app's update-info
 * model, kept as a type parameter so the domain layer does not depend on a feature type.
 */
interface AppUpdateManager<T> {
    suspend fun checkForUpdate(isManual: Boolean = false): T?
    fun dismiss(versionCode: Long)
    fun setAutoChecksDisabled(disabled: Boolean)
    fun areAutoChecksDisabled(): Boolean
    suspend fun downloadAndInstall(
        info: T,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<Unit>
}
