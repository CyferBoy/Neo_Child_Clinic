package com.neochildclinic.core.update

enum class UpdateType {
    UPDATE,
    DOWNGRADE
}

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val mandatory: Boolean,
    val minimumVersionCode: Long?,
    val downloadUrl: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val currentVersionCode: Long,
    val updateType: UpdateType
) {
    fun isRequired(): Boolean = mandatory
}
