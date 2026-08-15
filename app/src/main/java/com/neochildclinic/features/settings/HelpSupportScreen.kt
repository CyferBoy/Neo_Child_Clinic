package com.neochildclinic.features.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.neochildclinic.BuildConfig
import com.neochildclinic.core.ui.AppBackground

private const val SUPPORT_EMAIL = "neochildclinic.sbg@gmail.com"

@Composable
fun HelpSupportScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    fun contactSupport() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$SUPPORT_EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, "Vaccine Manager Support")
            putExtra(
                Intent.EXTRA_TEXT,
                "Please describe the issue you are experiencing.\n\n" +
                    "App: Vaccine Manager\n" +
                    "Version: ${BuildConfig.VERSION_NAME}"
            )
        }
        context.startActivity(intent)
    }

    AppBackground {
        Scaffold(topBar = { SettingsDetailTopBar("Help & Support", onBack) }) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("Help & Support", style = MaterialTheme.typography.headlineSmall)
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Getting Started", style = MaterialTheme.typography.titleMedium)
                            Text("Use Vaccine Manager to manage patient records, vaccination records, due dates, vaccine inventory, notifications, synchronization and backups.")
                            Text("If you need help with a specific feature, contact support and include the app version.")
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Troubleshooting", style = MaterialTheme.typography.titleMedium)
                            Text("If synchronization, notifications, backup, login or an update does not work as expected, first check your internet connection and then contact support.")
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Contact Support", style = MaterialTheme.typography.titleMedium)
                            Text(SUPPORT_EMAIL, color = MaterialTheme.colorScheme.primary)
                            Button(onClick = { contactSupport() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Email Support")
                            }
                            Text(
                                "Your app version is included automatically to help with troubleshooting.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
