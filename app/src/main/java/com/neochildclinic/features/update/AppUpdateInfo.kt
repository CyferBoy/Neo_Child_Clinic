package com.neochildclinic.features.update

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val mandatory: Boolean,
    val minimumVersionCode: Long?,
    val downloadUrl: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val currentVersionCode: Long,
    // SHA-256 of the APK asset, from GitHub's asset "digest" field when present (not every
    // repo/release has this populated). Verified against the downloaded file before
    // install; null means "no digest available, skip verification" rather than a failure.
    val apkSha256: String? = null,
    // How many non-prerelease releases newer than the installed version exist - purely
    // informational (shown in the mandatory-update notice), NOT what decides mandatory.
    // Mandatory is decided solely by the X.0.0 major-version-tag rule below.
    val releasesBehind: Int = 0
)
