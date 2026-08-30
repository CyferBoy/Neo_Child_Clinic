package com.neochildclinic.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.update.AppUpdateInfo
import com.neochildclinic.core.update.UpdateType
import com.neochildclinic.features.settings.DownloadProgress

@Composable
fun AppUpdateDialog(
    info: AppUpdateInfo,
    installing: Boolean,
    progress: DownloadProgress,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    val isDowngrade = info.updateType == UpdateType.DOWNGRADE
    val title = when (info.updateType) {
        UpdateType.UPDATE -> if (info.mandatory) "Update Required" else "New Update Available"
        UpdateType.REUPDATE -> "Re-update Available"
        UpdateType.DOWNGRADE -> "Older Version Available"
    }
    val actionText = when {
        installing -> "Downloading…"
        info.updateType == UpdateType.UPDATE -> "Update Now"
        info.updateType == UpdateType.REUPDATE -> "Reinstall"
        else -> "Downgrade"
    }

    AlertDialog(
        onDismissRequest = { if (!info.mandatory && !installing) onLater() },
        title = { Text(title) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (installing) {
                    if (progress.percent >= 0) {
                        LinearProgressIndicator(
                            progress = { progress.percent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("${progress.percent}%", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Downloading update…", style = MaterialTheme.typography.bodySmall)
                    }
                    if (progress.totalBytes > 0) {
                        Text(
                            formatBytes(progress.downloadedBytes) + " / " + formatBytes(progress.totalBytes),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                }

                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Vaccine Manager ${info.versionName} is available.")
                    if (isDowngrade) {
                        Text(
                            "You currently have a newer version installed. Installing this release may remove newer features or fixes.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(info.releaseNotes)
                    if (info.mandatory) Text("This update is required to continue using the application.")
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdate, enabled = !installing) { Text(actionText) }
        },
        dismissButton = if (!info.mandatory) {
            { TextButton(onClick = onLater, enabled = !installing) { Text("Later") } }
        } else null
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.1f GB".format(mb / 1024.0)
}

/**
 * Section 3/8: the downgrade version-selection list. Excludes the installed version and
 * anything newer (already guaranteed by AppUpdateManager.listDowngradeVersions() only
 * returning UpdateType.DOWNGRADE entries), sorted newest-to-oldest by the caller.
 */
@Composable
fun DowngradeVersionListDialog(
    versions: List<AppUpdateInfo>,
    selectedVersionCode: Long?,
    onSelect: (AppUpdateInfo) -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Downgrade") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())
            ) {
                Text("Select a version to downgrade to:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                versions.forEach { version ->
                    val selected = version.versionCode == selectedVersionCode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected, onClick = { onSelect(version) })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(selected = selected, onClick = { onSelect(version) })
                        Column(Modifier.padding(start = 4.dp, top = 10.dp)) {
                            Text("v${version.versionName}", style = MaterialTheme.typography.bodyLarge)
                            version.publishedAt?.let {
                                Text(
                                    formatPublishedDate(it),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (version.releaseNotes.isNotBlank()) {
                                Text(
                                    version.releaseNotes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/**
 * Section 4/5/6: confirmation for a specific selected downgrade version. Change Version
 * returns to the list (list stays populated); Cancel abandons the whole flow; Downgrade is
 * the only action that starts the actual download/install.
 */
@Composable
fun DowngradeConfirmDialog(
    info: AppUpdateInfo,
    installing: Boolean,
    progress: DownloadProgress,
    onChangeVersion: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!installing) onCancel() },
        title = { Text("Downgrade") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (installing) {
                    if (progress.percent >= 0) {
                        LinearProgressIndicator(
                            progress = { progress.percent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("${progress.percent}%", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Downloading…", style = MaterialTheme.typography.bodySmall)
                    }
                    if (progress.totalBytes > 0) {
                        Text(
                            formatBytes(progress.downloadedBytes) + " / " + formatBytes(progress.totalBytes),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                }
                Text("Downgrading to Vaccine Manager v${info.versionName}")
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !installing) { Text("Downgrade") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onChangeVersion, enabled = !installing) { Text("Change Version") }
                TextButton(onClick = onCancel, enabled = !installing) { Text("Cancel") }
            }
        }
    )
}

private fun formatPublishedDate(iso: String): String = try {
    val parsed = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.ENGLISH).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.parse(iso)
    if (parsed != null) java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.ENGLISH).format(parsed) else iso
} catch (_: Exception) {
    iso
}
