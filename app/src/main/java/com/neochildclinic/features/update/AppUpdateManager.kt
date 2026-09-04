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
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val GITHUB_REPO_URL =
            "https://api.github.com/repos/CyferBoy/Neo_Child_Clinic"
        private const val GITHUB_LATEST_URL = "$GITHUB_REPO_URL/releases/latest"
        private const val PREFS = "app_update"
        private const val DISMISSED_VERSION_CODE = "dismissed_version_code"
        private const val APK_FILE = "neo-child-clinic-update.apk"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun openGetConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Neo-Child-Clinic-Android")
        }

    private fun fetchJsonObject(url: String): JSONObject? {
        val connection = openGetConnection(url)
        return try {
            if (connection.responseCode !in 200..299) null
            else JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchJsonArray(url: String): org.json.JSONArray? {
        val connection = openGetConnection(url)
        return try {
            if (connection.responseCode !in 200..299) null
            else org.json.JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    // Shared release-JSON -> AppUpdateInfo parser, used by the "latest" check, the
    // re-update check (a specific tag), and the downgrade list (every release) - so all
    // three read release data, the APK asset, and the version/updateType the same way,
    // rather than three separate parsing implementations.
    private fun parseRelease(json: JSONObject, currentVersionCode: Long): AppUpdateInfo? {
        val rawTag = json.optString("tag_name").trim()
        val tagName = if (rawTag.startsWith("v", ignoreCase = true)) rawTag.substring(1).trim() else rawTag
        val versionCode = extractVersionCode(json, tagName) ?: return null
        val assets = json.optJSONArray("assets") ?: return null
        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url")
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

        // Semantic (major*1_000_000 + minor*1_000 + patch) comparison via versionCode, not
        // string comparison - correctly orders e.g. 0.4.10 above 0.4.9.
        val updateType = when {
            versionCode > currentVersionCode -> UpdateType.UPDATE
            versionCode == currentVersionCode -> UpdateType.REUPDATE
            else -> UpdateType.DOWNGRADE
        }

        val belowMinimumVersion = minimumVersionCode != null && currentVersionCode < minimumVersionCode
        val isMajorVersionTag = tagName.matches(Regex("^\\d+\\.0\\.0$"))
        val required = updateType == UpdateType.UPDATE &&
            (explicitlyMandatory || belowMinimumVersion || isMajorVersionTag)

        return AppUpdateInfo(
            versionName = tagName,
            versionCode = versionCode,
            mandatory = required,
            minimumVersionCode = minimumVersionCode,
            downloadUrl = downloadUrl,
            releaseNotes = cleanReleaseNotes(body),
            htmlUrl = json.optString("html_url"),
            currentVersionCode = currentVersionCode,
            updateType = updateType,
            publishedAt = json.optString("published_at").ifBlank { null }
        )
    }

    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val json = fetchJsonObject(GITHUB_LATEST_URL) ?: return@withContext null
        val info = parseRelease(json, currentVersionCode()) ?: return@withContext null

        val dismissed = prefs.getLong(DISMISSED_VERSION_CODE, -1L)
        if (info.updateType == UpdateType.UPDATE && !info.mandatory && dismissed == info.versionCode) {
            return@withContext null
        }
        info
    }

    /**
     * Fresh check for the release matching the currently installed version (Re-update).
     * Returns null if no release exists for the installed version, or it exists but has no
     * downloadable APK asset (e.g. removed from GitHub since it was installed).
     */
    /**
     * Fresh check for the release matching the currently installed version (Re-update).
     * Returns null if no release exists for the installed version, or it exists but has no
     * downloadable APK asset (e.g. removed from GitHub since it was installed).
     *
     * Deliberately does NOT do a `releases/tags/{tag}` lookup - that endpoint requires an
     * exact, case-sensitive tag match, and this repo's tags aren't consistently cased (e.g.
     * "V0.5.0" vs "v0.5.0"), plus currentVersionName() includes the debug build's "-debug"
     * suffix, which would never match any real release tag anyway. Instead this reuses the
     * same "list all releases, compare by versionCode" approach as listDowngradeVersions()
     * below, which is exactly as robust to tag naming/casing since it never constructs a
     * tag-name URL at all.
     */
    suspend fun checkForReupdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val currentCode = currentVersionCode()
        val array = fetchJsonArray("$GITHUB_REPO_URL/releases?per_page=100") ?: return@withContext null
        (0 until array.length())
            .mapNotNull { i -> array.optJSONObject(i)?.let { parseRelease(it, currentCode) } }
            .firstOrNull { it.updateType == UpdateType.REUPDATE }
    }

    /**
     * All releases older than the currently installed version, newest-first, that have a
     * downloadable APK asset - i.e. the valid downgrade candidates.
     */
    suspend fun listDowngradeVersions(): List<AppUpdateInfo> = withContext(Dispatchers.IO) {
        val currentVersionCode = currentVersionCode()
        val array = fetchJsonArray("$GITHUB_REPO_URL/releases?per_page=100") ?: return@withContext emptyList()
        (0 until array.length())
            .mapNotNull { i -> array.optJSONObject(i)?.let { parseRelease(it, currentVersionCode) } }
            .filter { it.updateType == UpdateType.DOWNGRADE }
            .sortedByDescending { it.versionCode }
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

            val apkFile = File(context.cacheDir, APK_FILE)
            if (apkFile.exists()) apkFile.delete()

            val connection = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.android.package-archive")
                setRequestProperty("User-Agent", "Neo-Child-Clinic-Android")
            }

            try {
                if (connection.responseCode !in 200..299) {
                    error("Download failed: HTTP ${connection.responseCode}")
                }
                val totalBytes = connection.contentLengthLong
                var downloadedBytes = 0L
                var lastReported = -1
                connection.inputStream.use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
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

                // The read loop above only proves the stream ended - not that it ended
                // where it should have. Without this, a connection dropped mid-transfer
                // (no exception, just a short read) would silently hand a truncated/empty
                // APK to PackageInstaller instead of failing here with a clear reason.
                if (downloadedBytes <= 0L) {
                    error("Download produced an empty file. Please try again.")
                }
                if (totalBytes > 0 && downloadedBytes != totalBytes) {
                    error("Download incomplete ($downloadedBytes of $totalBytes bytes). Please try again.")
                }

                onProgress(100, downloadedBytes, totalBytes)
            } finally {
                connection.disconnect()
            }

            // Only reachable once the block above completes without throwing, i.e. the
            // APK is fully downloaded and verified complete - always move straight on to
            // handing it to PackageInstaller rather than requiring a separate step/tap.
            installWithPackageInstaller(apkFile, info)
        }
    }

    private fun installWithPackageInstaller(apkFile: File, infoForInstall: AppUpdateInfo) {
        if (infoForInstall.updateType == UpdateType.DOWNGRADE && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            error("Installing an older version requires Android 10 or newer.")
        }
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (apkFile.length() > 0) setSize(apkFile.length())
            if (infoForInstall.updateType == UpdateType.DOWNGRADE && Build.VERSION.SDK_INT >= 34) {
                try {
                    val method = this::class.java.getMethod("setRequestDowngrade", Boolean::class.javaPrimitiveType)
                    method.invoke(this, true)
                } catch (_: Exception) { }
            }
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

    private fun currentVersionName(): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""

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
