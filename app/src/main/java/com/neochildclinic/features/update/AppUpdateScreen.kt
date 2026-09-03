package com.neochildclinic.features.update

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.features.settings.SettingsDetailTopBar
import com.neochildclinic.features.update.AppUpdateDialog
import com.neochildclinic.features.update.DowngradeConfirmDialog
import com.neochildclinic.features.update.DowngradeVersionListDialog

@Composable
fun AppUpdateScreen(onBack: () -> Unit, viewModel: AppUpdateViewModel) {
    val checking by viewModel.checking.collectAsState()
    val installing by viewModel.installing.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val message by viewModel.message.collectAsState()
    val upToDate by viewModel.upToDate.collectAsState()
    val reupdateInfo by viewModel.reupdateInfo.collectAsState()
    val downgradeVersions by viewModel.downgradeVersions.collectAsState()
    val selectedDowngrade by viewModel.selectedDowngrade.collectAsState()
    val highlightedVersionCode by viewModel.highlightedVersionCode.collectAsState()
    val noDowngradeAvailable by viewModel.noDowngradeAvailable.collectAsState()

    LaunchedEffect(Unit) { viewModel.checkForUpdates(isManual = true) }

    // No app-wide AppUpdateDialog (updateInfo) rendered here: that one is MainActivity's
    // silent-nag dialog and rendering it again here would duplicate it. reupdateInfo and the
    // downgrade dialogs below are their own dedicated state, only ever populated by explicit
    // taps on this screen, so they're safe to render here without any such conflict.

    // Section 1: the mandatory first result of a manual check - "up to date" with the
    // Re-update / Downgrade / OK choices. Never skipped straight to Re-update Available.
    if (upToDate) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpToDate() },
            title = { Text("App Updates") },
            text = { Text("Your application is up to date.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissUpToDate() }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.reupdate() }) { Text("Re-update") }
                    TextButton(onClick = { viewModel.startDowngrade() }) { Text("Downgrade") }
                }
            }
        )
    }

    // Section 2: Re-update Available - reuses the existing AppUpdateDialog (already handles
    // UpdateType.REUPDATE with the right title/button text), driven by its own state so it
    // only appears after the explicit Re-update tap and fresh check above.
    reupdateInfo?.let { info ->
        AppUpdateDialog(
            info = info,
            installing = installing,
            progress = downloadProgress,
            onUpdate = { viewModel.installReupdate() },
            onLater = { viewModel.dismissReupdate() }
        )
    }

    // Section 3/8: downgrade version list, or "no previous versions" if none exist.
    downgradeVersions?.let { versions ->
        if (selectedDowngrade == null) {
            DowngradeVersionListDialog(
                versions = versions,
                selectedVersionCode = highlightedVersionCode,
                onSelect = { viewModel.selectDowngradeVersion(it) },
                onCancel = { viewModel.cancelDowngradeList() }
            )
        }
    }
    if (noDowngradeAvailable) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissNoDowngradeAvailable() },
            title = { Text("Downgrade") },
            text = { Text("No previous versions are available for downgrade.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissNoDowngradeAvailable() }) { Text("OK") }
            }
        )
    }

    // Section 4/5/6: confirmation for the selected version. Change Version goes back to the
    // list above (still populated); only Downgrade here starts the real download/install.
    selectedDowngrade?.let { info ->
        DowngradeConfirmDialog(
            info = info,
            installing = installing,
            progress = downloadProgress,
            onChangeVersion = { viewModel.changeDowngradeVersion() },
            onCancel = { viewModel.cancelDowngradeConfirm() },
            onConfirm = { viewModel.confirmDowngrade() }
        )
    }

    // Generic single-OK message dialog (errors from any of the checks/downloads above).
    message?.let {
        AlertDialog(
            onDismissRequest = { viewModel.clearMessage() },
            title = { Text("App Updates") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { viewModel.clearMessage() }) { Text("OK") } }
        )
    }

    AppBackground {
        Scaffold(topBar = { SettingsDetailTopBar("Check for Updates", onBack) }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("App Updates", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (checking) "Checking for the latest version…" else
                        "Check whether a newer Vaccine Manager release is available.",
                    style = MaterialTheme.typography.bodyLarge
                )
                OutlinedButton(
                    onClick = { viewModel.checkForUpdates(isManual = true) },
                    enabled = !checking && !installing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (checking) "Checking…" else "Check Again")
                }
                Text(
                    "Version: ${com.neochildclinic.BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
