package com.neochildclinic.features.update

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.features.settings.SettingsDetailTopBar

@Composable
fun AppUpdateScreen(onBack: () -> Unit, viewModel: AppUpdateViewModel) {
    val checking by viewModel.checking.collectAsState()
    val installing by viewModel.installing.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val message by viewModel.message.collectAsState()
    val upToDate by viewModel.upToDate.collectAsState()
    val autoChecksEnabled by viewModel.autoChecksEnabled.collectAsState()

    LaunchedEffect(Unit) { viewModel.checkForUpdates(isManual = true) }

    // No app-wide AppUpdateDialog (updateInfo) rendered here: that one is MainActivity's
    // silent-nag dialog and rendering it again here would duplicate it.

    // The mandatory first result of a manual check when the installed version is already
    // the latest available.
    if (upToDate) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpToDate() },
            title = { Text("App Updates") },
            text = { Text("Your application is up to date.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissUpToDate() }) { Text("OK") }
            }
        )
    }

    // Generic single-OK message dialog (errors from the check/download above).
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Automatic update checks", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Show a heads-up when a new version is found on launch. " +
                                "Turned off automatically if you tap \"Don't remind me\" on that popup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoChecksEnabled,
                        onCheckedChange = { viewModel.setAutoChecksEnabled(it) }
                    )
                }
            }
        }
    }
}
