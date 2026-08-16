package com.neochildclinic.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
fun LockScreen(onAuthenticate: () -> Unit, onPasswordAuthenticate: (String) -> Unit) {
    AppBackground {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "App Locked",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Please authenticate to continue",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(48.dp))
                var showPasswordDialog by remember { mutableStateOf(false) }
                var password by remember { mutableStateOf("") }

                Button(
                    onClick = onAuthenticate,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Fingerprint, null)
                    Spacer(Modifier.width(12.dp))
                    Text("Use fingerprint / device credential")
                }

                Spacer(Modifier.height(12.dp))

                TextButton(onClick = { password = ""; showPasswordDialog = true }) {
                    Icon(Icons.Default.Password, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Use account password")
                }

                if (showPasswordDialog) {
                    AlertDialog(
                        onDismissRequest = { showPasswordDialog = false },
                        title = { Text("Account password") },
                        text = {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation()
                            )
                        },
                        confirmButton = {
                            TextButton(
                                enabled = password.isNotEmpty(),
                                onClick = {
                                    val value = password
                                    password = ""
                                    showPasswordDialog = false
                                    onPasswordAuthenticate(value)
                                }
                            ) { Text("Unlock") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPasswordDialog = false }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }
}
