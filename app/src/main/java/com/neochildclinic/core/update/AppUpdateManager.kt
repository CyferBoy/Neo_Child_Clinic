package com.neochildclinic.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
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
        private const val GITHUB_LATEST_URL =
            "https://api.github.com/repos/CyferBoy/Vaccine_Manager/releases/latest"
        private const val PREFS = "app_update"
        private const val DISMISSED_VERSION_CODE = "dismissed_version_code"
        private const val APK_FILE = "vaccine-manager-update.apk"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val connection = (URL(GITHUB_LATEST_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "VaccineManager-Android")
        }

        try {
            if (connection.responseCode !in 200..299) return@withContext null

            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tagName = json.optString("tag_name").removePrefix("v").trim()
            val versionCode = extractVersionCode(json, tagName) ?: return@withContext null

            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
            val downloadUrl = apkUrl ?: return@withContext null

            val body = json.optString("body")
            val mandatory = Regex(
                "(?im)^\\s*update-type\\s*:\\s*mandatory\\s*$"
            ).containsMatchIn(body)

            val minimumVersionCode = Regex(
                "(?im)^\\s*minimum-version-code\\s*:\\s*(\\d+)\\s*$"
            ).find(body)?.groupValues?.getOrNull(1)?.toLongOrNull()

            val currentVersionCode = currentVersionCode()
            if (versionCode <= currentVersionCode) return@withContext null

            val required = mandatory || (minimumVersionCode != null && currentVersionCode < minimumVersionCode)
            val dismissed = prefs.getLong(DISMISSED_VERSION_CODE, -1L)
            if (!required && dismissed == versionCode) return@withContext null

            AppUpdateInfo(
                versionName = tagName,
                versionCode = versionCode,
                mandatory = required,
                minimumVersionCode = minimumVersionCode,
                downloadUrl = downloadUrl,
                releaseNotes = cleanReleaseNotes(body),
                htmlUrl = json.optString("html_url")
            )
        } finally {
            connection.disconnect()
        }
    }

    fun dismiss(versionCode: Long) {
        prefs.edit().putLong(DISMISSED_VERSION_CODE, versionCode).apply()
    }

    suspend fun downloadAndInstall(info: AppUpdateInfo): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val apkFile = File(context.cacheDir, APK_FILE)
            if (apkFile.exists()) apkFile.delete()

            val connection = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.android.package-archive")
                setRequestProperty("User-Agent", "VaccineManager-Android")
            }

            try {
                if (connection.responseCode !in 200..299) {
                    error("Download failed: HTTP ${connection.responseCode}")
                }
                connection.inputStream.use { input ->
                    apkFile.outputStream().use { output -> input.copyTo(output) }
                }
            } finally {
                connection.disconnect()
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            withContext(Dispatchers.Main) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !context.packageManager.canRequestPackageInstalls()
                ) {
                    val settingsIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    error("Please allow this app to install unknown apps, then tap Update again.")
                }

                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(installIntent)
            }
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
        // Preferred: a release body line such as: version-code: 3
        val bodyVersion = Regex(
            "(?im)^\\s*version-code\\s*:\\s*(\\d+)\\s*$"
        ).find(json.optString("body"))?.groupValues?.getOrNull(1)?.toLongOrNull()
        if (bodyVersion != null) return bodyVersion

        // Fallback for releases using a simple semantic tag: 1.2.3 -> 10203.
        // This is only a fallback; explicit version-code is recommended.
        val parts = tagName.split(".")
        if (parts.size >= 2 && parts.take(3).all { it.toIntOrNull() != null }) {
            val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            return major * 1_000_000L + minor * 1_000L + patch
        }
        return null
    }

    private fun cleanReleaseNotes(body: String): String {
        val withoutControlLines = body.lines()
            .filterNot {
                val t = it.trim()
                t.startsWith("version-code:", ignoreCase = true) ||
                    t.startsWith("update-type:", ignoreCase = true) ||
                    t.startsWith("minimum-version-code:", ignoreCase = true)
            }
        return withoutControlLines.joinToString("\n").trim()
            .ifBlank { "Bug fixes and improvements." }
    }
}
