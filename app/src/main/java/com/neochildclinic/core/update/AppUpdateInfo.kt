package com.neochildclinic.core.update

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val mandatory: Boolean,
    val minimumVersionCode: Long?,
    val downloadUrl: String,
    val releaseNotes: String,
    val htmlUrl: String
) {
    fun isRequired(currentVersionCode: Long): Boolean =
        mandatory || (minimumVersionCode != null && currentVersionCode < minimumVersionCode)
}
