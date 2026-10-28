package com.neochildclinic.core.ui

import com.neochildclinic.core.designsystem.*

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.neochildclinic.R

@Composable
fun LockScreen(
    onAuthenticate: () -> Unit,
    onPasswordAuthenticate: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.primary
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Subtle decorative background circles
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 60.dp, y = (-40).dp)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-40).dp, y = 40.dp)
                    .background(Color.White.copy(alpha = 0.04f), CircleShape)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = Spacing.xxl, horizontal = Spacing.xl)
            ) {
                // Top Clinic Tag
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = Spacing.xl)
                ) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.16f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "NEO CHILD CLINIC",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        letterSpacing = 1.2.sp
                    )
                }

                // Middle Lock / Biometric Section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .background(Color.White.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = "Fingerprint unlock",
                            modifier = Modifier.size(54.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Spacer(Modifier.height(Spacing.xl))

                    Text(
                        "Welcome back",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Neo Child Clinic",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Unlock to continue to your clinic.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                    )

                    Spacer(Modifier.height(Spacing.xxl))

                    var showPasswordDialog by remember { mutableStateOf(false) }
                    var password by remember { mutableStateOf("") }

                    Button(
                        onClick = onAuthenticate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = ButtonShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimary,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Fingerprint, null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            "Unlock with fingerprint",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(Spacing.sm))

                    TextButton(
                        onClick = { password = ""; showPasswordDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Icon(Icons.Default.Password, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Use account password instead")
                    }

                    if (showPasswordDialog) {
                        AlertDialog(
                            onDismissRequest = { showPasswordDialog = false },
                            shape = DialogShape,
                            title = { Text("Account password") },
                            text = {
                                StandardTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = "Password",
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
                                ) { Text("Unlock", fontWeight = FontWeight.Bold) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPasswordDialog = false }) { Text("Cancel") }
                            }
                        )
                    }
                }

                // Footer Privacy Note
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Clinic data is encrypted on this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f)
                    )
                    Text(
                        "Locked after 15 minutes of inactivity",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
