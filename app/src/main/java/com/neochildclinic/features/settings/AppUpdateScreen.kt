package com.neochildclinic.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.ui.AppBackground

@Composable
fun AppUpdateScreen(onBack: () -> Unit, viewModel: AppUpdateViewModel) {
    val checking by viewModel.checking.collectAsState()
    val installing by viewModel.installing.collectAsState()
    val message by viewModel.message.collectAsState()

    LaunchedEffect(Unit) { viewModel.checkForUpdates(isManual = true) }

    // No AppUpdateDialog here: this screen shares the same AppUpdateViewModel
    // instance MainActivity uses for its app-wide dialog, so rendering a second
    // one here would just duplicate it on top of itself. Only the manual-check
    // result message ("up to date" / error) is specific to this screen.
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
