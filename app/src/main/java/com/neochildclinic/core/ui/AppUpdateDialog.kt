package com.neochildclinic.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    val title = if (isDowngrade) "Older Version Available" else {
        if (info.mandatory) "Update Required" else "New Update Available"
    }
    val actionText = when {
        installing -> "Downloading…"
        info.updateType == UpdateType.UPDATE -> "Update Now"
        else -> "Install Older Version"
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
