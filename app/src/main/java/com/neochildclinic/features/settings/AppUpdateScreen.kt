package com.neochildclinic.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.AppUpdateDialog

@Composable
fun AppUpdateScreen(onBack: () -> Unit, viewModel: AppUpdateViewModel = hiltViewModel()) {
    val info by viewModel.updateInfo.collectAsState()
    val checking by viewModel.checking.collectAsState()
    val installing by viewModel.installing.collectAsState()
    val message by viewModel.message.collectAsState()

    LaunchedEffect(Unit) { viewModel.checkForUpdates(isManual = true) }

    info?.let {
        AppUpdateDialog(
            info = it,
            installing = installing,
            onUpdate = { viewModel.installUpdate() },
            onLater = { viewModel.dismissUpdate() }
        )
    }

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
