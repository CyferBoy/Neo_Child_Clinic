package com.neochildclinic.features.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.features.update.AppUpdateInfo
import com.neochildclinic.BuildConfig
import com.neochildclinic.features.update.DownloadProgress

@Composable
fun AppUpdateDialog(
    info: AppUpdateInfo,
    installing: Boolean,
    progress: DownloadProgress,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onDontRemindMe: () -> Unit = onLater
) {
    val title = if (info.mandatory) "Update Required" else "Update Available"

    AlertDialog(
        onDismissRequest = { if (!info.mandatory && !installing) onLater() },
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Default.SystemUpdate,
                        contentDescription = null,
                        modifier = Modifier.padding(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    if (!installing) {
                        Text(
                            if (info.mandatory) "This is a major update and is required"
                            else "A new version is ready",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (info.mandatory) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Current", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text("→", style = MaterialTheme.typography.headlineMedium)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "New",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                "v${info.versionName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (installing) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Downloading…",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (progress.percent >= 0) {
                                LinearProgressIndicator(
                                    progress = { progress.percent / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        if (progress.totalBytes > 0)
                                            "${formatBytes(progress.downloadedBytes)} / ${formatBytes(progress.totalBytes)}"
                                        else
                                            formatBytes(progress.downloadedBytes),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        "${progress.percent}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    "Preparing download…",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "What's New",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 1.dp
                        ) {
                            Text(
                                formatChangelog(info.releaseNotes),
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(14.dp),
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp)
                            )
                        }
                        if (info.mandatory) {
                            Text(
                                if (info.releasesBehind > 1) {
                                    "This update is required to continue using the application (${info.releasesBehind} releases behind)."
                                } else {
                                    "This update is required to continue using the application."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                enabled = !installing
            ) {
                Text(
                    when {
                        installing -> "Downloading…"
                        else -> "Update"
                    }
                )
            }
        },
        dismissButton = if (!info.mandatory) {
            {
                if (installing) {
                    TextButton(onClick = onLater, enabled = true) { Text("Cancel") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onDontRemindMe) {
                            Text("Don't remind me", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = onLater) { Text("Later") }
                    }
                }
            }
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

// Ported from SpotiFLAC-Mobile's update_dialog.dart _formatChangelog(): turns a raw GitHub
// release body into a short, clean bulleted summary instead of showing raw markdown.
private val whatsNewPattern = Regex("(?i)###?\\s*What'?s\\s*New\\s*\\n")
private val cutoffPattern = Regex("(?i)\\n---|\\n###?\\s*Downloads")
private val sectionPattern = Regex("^#{1,3}\\s*(.+)$")
private val listPattern = Regex("^[-*]\\s+(.+)$")
private val subListPattern = Regex("^\\s+[-*]\\s+(.+)$")
private val boldPattern = Regex("\\*\\*([^*]+)\\*\\*")
private val codePattern = Regex("`([^`]+)`")

private fun formatChangelog(changelog: String, emptyFallback: String = "See release notes for details."): String {
    var content = changelog

    whatsNewPattern.find(content)?.let { content = content.substring(it.range.last + 1) }
    cutoffPattern.find(content)?.let { content = content.substring(0, it.range.first) }

    val formattedLines = mutableListOf<String>()
    for (rawLine in content.split("\n")) {
        val line = rawLine.trim()
        if (line.isEmpty()) continue

        val sectionMatch = sectionPattern.find(line)
        if (sectionMatch != null) {
            val section = sectionMatch.groupValues.getOrNull(1)?.trim()
            if (!section.isNullOrEmpty()) {
                if (formattedLines.isNotEmpty()) formattedLines.add("")
                formattedLines.add(section)
            }
            continue
        }

        val listMatch = listPattern.find(line)
        if (listMatch != null) {
            var itemText = listMatch.groupValues.getOrElse(1) { "" }
            itemText = boldPattern.replace(itemText) { it.groupValues[1] }
            itemText = codePattern.replace(itemText) { it.groupValues[1] }
            formattedLines.add("• $itemText")
            continue
        }

        val subListMatch = subListPattern.find(line)
        if (subListMatch != null) {
            var itemText = subListMatch.groupValues.getOrElse(1) { "" }
            itemText = boldPattern.replace(itemText) { it.groupValues[1] }
            formattedLines.add("  - $itemText")
            continue
        }
    }

    var formatted = formattedLines.joinToString("\n").trim()
    if (formatted.length > 2000) formatted = formatted.substring(0, 2000) + "..."

    return formatted.ifEmpty { emptyFallback }
}
