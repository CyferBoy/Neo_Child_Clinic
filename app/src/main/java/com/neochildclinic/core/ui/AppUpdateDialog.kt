package com.neochildclinic.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.update.AppUpdateInfo

@Composable
fun AppUpdateDialog(
    info: AppUpdateInfo,
    installing: Boolean,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!info.mandatory && !installing) onLater() },
        title = {
            Text(if (info.mandatory) "Update Required" else "New Update Available")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Vaccine Manager ${info.versionName} is available.")
                Text(info.releaseNotes)
                if (info.mandatory) {
                    Text("This update is required to continue using the application.")
                }
                if (installing) {
                    CircularProgressIndicator()
                    Text("Downloading update…")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                enabled = !installing
            ) {
                Text(if (installing) "Downloading…" else "Update Now")
            }
        },
        dismissButton = if (!info.mandatory) {
            {
                TextButton(onClick = onLater, enabled = !installing) {
                    Text("Later")
                }
            }
        } else null
    )
}
