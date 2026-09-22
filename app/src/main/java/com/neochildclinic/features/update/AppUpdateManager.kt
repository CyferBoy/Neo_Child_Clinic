package com.neochildclinic.features.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Modeled on SpotiFLAC-Mobile's update_checker.dart/apk_downloader.dart: a cached,
 * ETag-revalidated release list; resumable, SHA-256-verified APK downloads; and a
 * "releases behind" count for information only. The one deliberate divergence, per
 * explicit instruction: mandatory/forced updates are decided solely by the installed
 * app's own X.0.0 major-version-tag rule (already existed here before this port), not by
 * SpotiFLAC's "3+ releases behind forces an update" policy.
 */
@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val GITHUB_REPO_URL =
            "https://api.github.com/repos/CyferBoy/Neo_Child_Clinic"
        private const val GITHUB_RELEASES_LIST_URL = "$GITHUB_REPO_URL/releases?per_page=30"
        private const val PREFS = "app_update"
        private const val DISMISSED_VERSION_CODE = "dismissed_version_code"
        private const val AUTO_CHECKS_DISABLED = "auto_checks_disabled"
        private const val CACHED_RELEASES_BODY = "cached_releases_body"
        private const val CACHED_RELEASES_AT = "cached_releases_at"
        private const val CACHED_RELEASES_ETAG = "cached_releases_etag"
        private const val APK_FILE = "neo-child-clinic-update.apk"
        private const val APK_PART_FILE = "$APK_FILE.part"
        private const val APK_PART_METADATA_FILE = "$APK_PART_FILE.json"

        // Release cadence here is days, not minutes, and the payload is tens of KB - serve
        // from cache within this window and only revalidate (via ETag) after it expires,
        // same reasoning as SpotiFLAC's UpdateChecker._cacheTtl.
        private const val CACHE_TTL_MS = 6L * 60 * 60 * 1000
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun openGetConnection(url: String, extraHeaders: Map<String, String> = emptyMap()): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Neo-Child-Clinic-Android")
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
        }

    /**
     * Cached-then-conditional-GET-then-stale-on-failure fetch of the releases list body,
     * mirroring UpdateChecker._fetchReleasesBody(): fresh cache within CACHE_TTL_MS is
     * served with no network call at all; past that, a conditional GET (If-None-Match) can
     * still return 304 and just refresh the cache timestamp; and if GitHub is unreachable
     * or errors, a stale cached body is still better than failing the whole update check.
     */
    private fun fetchReleasesBody(): String? {
        val cachedBody = prefs.getString(CACHED_RELEASES_BODY, null)
        val cachedAt = prefs.getLong(CACHED_RELEASES_AT, 0L)
        val age = System.currentTimeMillis() - cachedAt
        if (cachedBody != null && age < CACHE_TTL_MS) {
            return cachedBody
        }

        val cachedEtag = prefs.getString(CACHED_RELEASES_ETAG, null)
        val headers = if (cachedBody != null && cachedEtag != null) {
            mapOf("If-None-Match" to cachedEtag)
        } else emptyMap()

        val connection = openGetConnection(GITHUB_RELEASES_LIST_URL, headers)
        return try {
            when (connection.responseCode) {
                304 -> {
                    if (cachedBody != null) {
                        prefs.edit().putLong(CACHED_RELEASES_AT, System.currentTimeMillis()).apply()
                    }
                    cachedBody
                }
                in 200..299 -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val editor = prefs.edit()
                        .putString(CACHED_RELEASES_BODY, body)
                        .putLong(CACHED_RELEASES_AT, System.currentTimeMillis())
                    connection.getHeaderField("ETag")?.let { editor.putString(CACHED_RELEASES_ETAG, it) }
                    editor.apply()
                    body
                }
                else -> cachedBody // stale is better than none for the update prompt
            }
        } catch (e: Exception) {
            cachedBody
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Whether the automatic (silent, non-manual) check should run at all - the "Don't
     * remind me" choice on the update dialog. A manual check (the Check for Updates
     * button on the App Update screen) always runs regardless of this flag.
     */
    fun areAutoChecksDisabled(): Boolean = prefs.getBoolean(AUTO_CHECKS_DISABLED, false)

    fun setAutoChecksDisabled(disabled: Boolean) {
        prefs.edit().putBoolean(AUTO_CHECKS_DISABLED, disabled).apply()
    }

    // Shared release-JSON -> AppUpdateInfo parser. Returns null for any release that is
    // not strictly newer than the installed version - there is no reinstall-same-version
    // or downgrade-to-older-version path, so a same-or-older release isn't a candidate.
    private fun parseRelease(json: JSONObject, currentVersionCode: Long, releasesBehind: Int): AppUpdateInfo? {
        val rawTag = json.optString("tag_name").trim()
        val tagName = if (rawTag.startsWith("v", ignoreCase = true)) rawTag.substring(1).trim() else rawTag
        val versionCode = extractVersionCode(json, tagName) ?: return null
        if (versionCode <= currentVersionCode) return null
        val assets = json.optJSONArray("assets") ?: return null
        var apkUrl: String? = null
        var apkSha256: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url")
                apkSha256 = normalizeDigest(asset.optString("digest").takeIf { it.isNotBlank() })
                break
            }
        }
        val downloadUrl = apkUrl ?: return null
        val body = json.optString("body")
        val explicitlyMandatory = Regex(
            "(?im)^\\s*update-type\\s*:\\s*mandatory\\s*$"
        ).containsMatchIn(body)
        val minimumVersionCode = Regex(
            "(?im)^\\s*minimum-version-code\\s*:\\s*(\\d+)\\s*$"
        ).find(body)?.groupValues?.getOrNull(1)?.toLongOrNull()

        val belowMinimumVersion = minimumVersionCode != null && currentVersionCode < minimumVersionCode
        // The sole "forced update" trigger, by explicit instruction - NOT releasesBehind.
        val isMajorVersionTag = tagName.matches(Regex("^\\d+\\.0\\.0$"))
        val required = explicitlyMandatory || belowMinimumVersion || isMajorVersionTag

        return AppUpdateInfo(
            versionName = tagName,
            versionCode = versionCode,
            mandatory = required,
            minimumVersionCode = minimumVersionCode,
            downloadUrl = downloadUrl,
            releaseNotes = cleanReleaseNotes(body),
            htmlUrl = json.optString("html_url"),
            currentVersionCode = currentVersionCode,
            apkSha256 = apkSha256,
            releasesBehind = releasesBehind
        )
    }

    private fun normalizeDigest(digest: String?): String? {
        if (digest == null) return null
        val normalized = digest.trim().lowercase().removePrefix("sha256:")
        return if (Regex("^[a-f0-9]{64}$").matches(normalized)) normalized else null
    }

    suspend fun checkForUpdate(isManual: Boolean = false): AppUpdateInfo? = withContext(Dispatchers.IO) {
        if (!isManual && areAutoChecksDisabled()) return@withContext null

        val releasesJson = fetchReleasesBody() ?: return@withContext null
        val releases = try {
            JSONArray(releasesJson)
        } catch (e: Exception) {
            return@withContext null
        }
        if (releases.length() == 0) return@withContext null

        val currentCode = currentVersionCode()

        // Stable channel only: the first non-prerelease, non-draft release in the list.
        var candidate: JSONObject? = null
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            if (release.optBoolean("prerelease", false)) continue
            if (release.optBoolean("draft", false)) continue
            candidate = release
            break
        }
        val candidateJson = candidate ?: return@withContext null

        // How many stable releases are newer than the installed version - informational
        // only (shown in the mandatory-update notice), never what decides mandatory.
        var releasesBehind = 0
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            if (release.optBoolean("prerelease", false) || release.optBoolean("draft", false)) continue
            val rawTag = release.optString("tag_name").trim()
            val tagName = if (rawTag.startsWith("v", ignoreCase = true)) rawTag.substring(1).trim() else rawTag
            val code = extractVersionCode(release, tagName)
            if (code != null && code > currentCode) releasesBehind++
        }

        val info = parseRelease(candidateJson, currentCode, releasesBehind) ?: return@withContext null

        val dismissed = prefs.getLong(DISMISSED_VERSION_CODE, -1L)
        if (!info.mandatory && dismissed == info.versionCode) {
            return@withContext null
        }
        info
    }

    fun dismiss(versionCode: Long) {
        prefs.edit().putLong(DISMISSED_VERSION_CODE, versionCode).apply()
    }

    suspend fun downloadAndInstall(
        info: AppUpdateInfo,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                withContext(Dispatchers.Main) {
                    val settingsIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(settingsIntent)
                }
                error("Please allow this app to install unknown apps, then tap Update again.")
            }

            val uri = runCatching { URL(info.downloadUrl) }.getOrNull()
            if (uri == null || uri.protocol != "https") {
                error("Refusing to download from a non-HTTPS URL.")
            }

            val apkFile = File(context.cacheDir, APK_FILE)
            val partFile = File(context.cacheDir, APK_PART_FILE)
            val metadataFile = File(context.cacheDir, APK_PART_METADATA_FILE)
            if (apkFile.exists()) apkFile.delete()

            downloadWithResume(info, partFile, metadataFile, onProgress)

            if (!partFile.renameTo(apkFile)) {
                // Cross-filesystem rename can fail even within the same app's cacheDir in
                // rare cases (e.g. different mount points under scoped storage on some
                // OEMs) - copy+delete is the safe fallback.
                partFile.copyTo(apkFile, overwrite = true)
                partFile.delete()
            }
            metadataFile.delete()

            // Only reachable once the APK is fully downloaded, size-verified, and
            // (when a digest was available) SHA-256-verified - always move straight on to
            // handing it to PackageInstaller rather than requiring a separate step/tap.
            installWithPackageInstaller(apkFile)
        }.also { result ->
            // runCatching's catch-all also swallows CancellationException (thrown above by
            // ensureActive() when the caller cancels this Job, e.g. tapping Cancel while
            // installing). Left as a plain Result.failure, it would surface through
            // .onFailure as a bogus "download failed" error message instead of just quietly
            // stopping - rethrowing preserves normal coroutine cancellation semantics.
            val error = result.exceptionOrNull()
            if (error is kotlinx.coroutines.CancellationException) throw error
        }
    }

    /**
     * Downloads into [partFile] with HTTP Range resume support, mirroring
     * ApkDownloader.downloadApk(): if a partial file from a previous attempt exists for
     * the *same* URL (tracked via the metadata sidecar), resume it with a conditional
     * Range request; otherwise (or if the server can't/won't honor the resume) start over.
     * On success, verifies the total byte count and - when [AppUpdateInfo.apkSha256] is
     * available - the file's SHA-256 digest, before returning.
     */
    private suspend fun downloadWithResume(
        info: AppUpdateInfo,
        partFile: File,
        metadataFile: File,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ) {
        val metadata = readResumeMetadata(metadataFile)
        var resumeOffset = 0L
        if (partFile.exists() && metadata?.optString("url") == info.downloadUrl) {
            resumeOffset = partFile.length()
        } else {
            partFile.delete()
            metadataFile.delete()
        }

        val headers = buildMap {
            if (resumeOffset > 0) {
                put("Range", "bytes=$resumeOffset-")
                val etag = metadata?.optString("etag")?.takeIf { it.isNotBlank() }
                if (etag != null) put("If-Range", etag)
            }
        }
        val connection = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.android.package-archive")
            setRequestProperty("User-Agent", "Neo-Child-Clinic-Android")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode == 416) {
                // Server rejected the saved range - the partial is no longer valid
                // (e.g. the asset changed). Discard and let the caller retry from empty.
                partFile.delete(); metadataFile.delete()
                error("Download was interrupted and could not resume. Please try again.")
            }
            if (responseCode !in intArrayOf(200, 206)) {
                error("Download failed: HTTP $responseCode")
            }

            val isResume = resumeOffset > 0 && responseCode == 206 &&
                contentRangeStartsAt(connection.getHeaderField("Content-Range"), resumeOffset)
            if (resumeOffset > 0 && responseCode == 206 && !isResume) {
                // Server answered 206 but for a different range than requested (a buggy
                // or unexpected proxy) - never trust it blindly and silently append onto
                // the wrong offset. Discard and restart clean.
                partFile.delete(); metadataFile.delete()
            }
            val effectiveOffset = if (isResume) resumeOffset else 0L
            if (!isResume && partFile.exists()) partFile.delete()

            val contentLength = connection.contentLengthLong
            val totalBytes = if (contentLength > 0) contentLength + effectiveOffset else -1L

            val newEtag = connection.getHeaderField("ETag") ?: ""
            metadataFile.writeText(
                JSONObject().apply {
                    put("url", info.downloadUrl)
                    put("etag", newEtag)
                    put("total", totalBytes)
                }.toString()
            )

            var downloadedBytes = effectiveOffset
            var lastReported = -1
            connection.inputStream.use { input ->
                java.io.FileOutputStream(partFile, isResume).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        // Without this, cancelling the ViewModel's install Job (e.g.
                        // tapping Cancel while installing) has no effect on this loop - a
                        // raw blocking InputStream.read() doesn't observe coroutine
                        // cancellation on its own, so the download would keep running to
                        // completion in the background. downloadWithResume() is a suspend
                        // function called from inside withContext(Dispatchers.IO){} rather
                        // than lexically nested in it, so (unlike a bare ensureActive()
                        // relying on an outer CoroutineScope receiver) this needs the
                        // current coroutine's context explicitly.
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        val percent = if (totalBytes > 0) {
                            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        } else -1
                        if (percent != lastReported) {
                            lastReported = percent
                            onProgress(percent, downloadedBytes, totalBytes)
                        }
                    }
                }
            }

            if (downloadedBytes <= 0L) {
                partFile.delete(); metadataFile.delete()
                error("Download produced an empty file. Please try again.")
            }
            if (totalBytes > 0 && downloadedBytes != totalBytes) {
                error("Download incomplete ($downloadedBytes of $totalBytes bytes). Please try again.")
            }

            info.apkSha256?.let { expected ->
                val actual = sha256Hex(partFile)
                if (!actual.equals(expected, ignoreCase = true)) {
                    partFile.delete(); metadataFile.delete()
                    error("Downloaded file failed integrity verification. Please try again.")
                }
            }

            onProgress(100, downloadedBytes, totalBytes)
        } finally {
            connection.disconnect()
        }
    }

    /** "bytes 500-999/1000" -> does the range actually start at [expectedOffset]? */
    private fun contentRangeStartsAt(contentRange: String?, expectedOffset: Long): Boolean {
        if (contentRange == null) return false
        val match = Regex("bytes\\s+(\\d+)-").find(contentRange) ?: return false
        return match.groupValues[1].toLongOrNull() == expectedOffset
    }

    private fun readResumeMetadata(metadataFile: File): JSONObject? {
        if (!metadataFile.exists()) return null
        return try {
            JSONObject(metadataFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun installWithPackageInstaller(apkFile: File) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (apkFile.length() > 0) setSize(apkFile.length())
        }
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)
        try {
            apkFile.inputStream().use { input ->
                session.openWrite("base.apk", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val callbackIntent = Intent(context, AppUpdateInstallReceiver::class.java).apply {
                action = AppUpdateInstallReceiver.ACTION_INSTALL_STATUS
                putExtra(AppUpdateInstallReceiver.EXTRA_SESSION_ID, sessionId)
            }
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, callbackIntent, pendingIntentFlags)
            session.commit(pendingIntent.intentSender)
        } catch (t: Throwable) {
            session.abandon()
            throw t
        } finally {
            session.close()
            if (apkFile.exists()) apkFile.delete()
        }
    }

    private fun currentVersionCode(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
        }

    private fun extractVersionCode(json: JSONObject, tagName: String): Long? {
        val bodyVersion = Regex(
            "(?im)^\\s*version-code\\s*:\\s*(\\d+)\\s*$"
        ).find(json.optString("body"))?.groupValues?.getOrNull(1)?.toLongOrNull()
        if (bodyVersion != null) return bodyVersion
        val parts = tagName.split(".")
        if (parts.size >= 2 && parts.take(3).all { it.toIntOrNull() != null }) {
            val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            return major * 1_000_000L + minor * 1_000L + patch
        }
        return null
    }

    private fun cleanReleaseNotes(body: String): String = body.lines()
        .filterNot {
            val t = it.trim()
            t.startsWith("version-code:", ignoreCase = true) ||
                t.startsWith("update-type:", ignoreCase = true) ||
                t.startsWith("minimum-version-code:", ignoreCase = true)
        }
        .joinToString("\n").trim().ifBlank { "Bug fixes and improvements." }
}
