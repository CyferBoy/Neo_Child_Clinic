package com.neochildclinic.features.update

enum class UpdateType {
    UPDATE,
    REUPDATE,
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
    val updateType: UpdateType,
    val publishedAt: String? = null
) {
    fun isRequired(): Boolean = mandatory
}
