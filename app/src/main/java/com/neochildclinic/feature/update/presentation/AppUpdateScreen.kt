package com.neochildclinic.feature.update.presentation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.BuildConfig
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.feature.update.data.AppUpdateInfo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface UpdateCardUiState {
    data object Idle : UpdateCardUiState
    data object Checking : UpdateCardUiState
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCardUiState
    data object UpToDate : UpdateCardUiState
}

private fun formatLastChecked(timestamp: Long): String {
    if (timestamp <= 0L) return "Checking for updates…"
    val now = System.currentTimeMillis()
    val diffMs = now - timestamp
    if (diffMs < 60_000L) return "Last checked just now"
    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
    return "Last checked today at $timeStr"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateScreen(onBack: () -> Unit, viewModel: AppUpdateViewModel) {
    val updateInfo by viewModel.updateInfo.collectAsState()
    val checking by viewModel.checking.collectAsState()
    val installing by viewModel.installing.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val message by viewModel.message.collectAsState()
    val upToDate by viewModel.upToDate.collectAsState()
    val lastCheckedTimestamp by viewModel.lastCheckedTimestamp.collectAsState()
    val autoChecksEnabled by viewModel.autoChecksEnabled.collectAsState()
    var showNotesAfterInstall by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.checkForUpdates(isManual = true)
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { viewModel.clearMessage() },
            shape = RoundedCornerShape(24.dp),
            title = { Text("App Updates") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { viewModel.clearMessage() }) { Text("OK") } }
        )
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                BackTopAppBar(
                    title = { Text("App Updates") },
                    onBack = onBack
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // 1. Current Version Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Text(
                            text = "Current version",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Neo Child Clinic ${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = "Installed",
                                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = formatLastChecked(lastCheckedTimestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 2. Animated Status Card (Checking / Update Available / Up-To-Date)
                val cardState = when {
                    checking -> UpdateCardUiState.Checking
                    updateInfo != null -> UpdateCardUiState.UpdateAvailable(updateInfo!!)
                    upToDate -> UpdateCardUiState.UpToDate
                    else -> UpdateCardUiState.Idle
                }

                AnimatedContent(
                    targetState = cardState,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(280)) + scaleIn(initialScale = 0.96f, animationSpec = tween(280)))
                            .togetherWith(fadeOut(animationSpec = tween(180)))
                    },
                    label = "updateStatusTransition"
                ) { state ->
                    when (state) {
                        UpdateCardUiState.Checking -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(Spacing.lg),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                                    ) {
                                        val infiniteTransition = rememberInfiniteTransition(label = "checkSpin")
                                        val rotation by infiniteTransition.animateFloat(
                                            initialValue = 0f,
                                            targetValue = 360f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(1000, easing = LinearEasing),
                                                repeatMode = RepeatMode.Restart
                                            ),
                                            label = "spinAngle"
                                        )
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            modifier = Modifier.size(42.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .graphicsLayer { rotationZ = rotation }
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = "Checking for updates…",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Checking release server for latest updates…",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }

                        is UpdateCardUiState.UpdateAvailable -> {
                            val info = state.info
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(Spacing.lg),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SystemUpdate,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column {
                                            Text(
                                                text = "Version ${info.versionName} is available",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Released recently",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                    Text(
                                        text = "What's new",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        val notes = if (info.releaseNotes.isNotBlank()) {
                                            info.releaseNotes.lines().filter { it.isNotBlank() }
                                        } else {
                                            listOf(
                                                "Due cards: completed and dismissed above search",
                                                "Borrowed vaccines integrated with inventory",
                                                "Refined app updates and personal reminders",
                                                "Security update included"
                                            )
                                        }

                                        notes.forEach { note ->
                                            Row(
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                            ) {
                                                Text(
                                                    text = "•",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = note.removePrefix("-").removePrefix("•").trim(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }

                                    if (installing) {
                                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                            LinearProgressIndicator(
                                                progress = { downloadProgress.percent / 100f },
                                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                            )
                                            Text(
                                                text = "Downloading update: ${downloadProgress.percent}%",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else {
                                        Button(
                                            onClick = { viewModel.installUpdate() },
                                            modifier = Modifier.fillMaxWidth().height(50.dp),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text(
                                                text = "Download & install",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        UpdateCardUiState.UpToDate -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                val checkmarkScale = remember { Animatable(0.2f) }
                                val haloScale = remember { Animatable(0.8f) }
                                val haloAlpha = remember { Animatable(0.6f) }

                                LaunchedEffect(lastCheckedTimestamp) {
                                    checkmarkScale.snapTo(0.2f)
                                    haloScale.snapTo(0.8f)
                                    haloAlpha.snapTo(0.6f)
                                    launch {
                                        checkmarkScale.animateTo(
                                            targetValue = 1f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )
                                    }
                                    launch {
                                        haloScale.animateTo(
                                            targetValue = 1.6f,
                                            animationSpec = tween(550, easing = FastOutSlowInEasing)
                                        )
                                    }
                                    launch {
                                        haloAlpha.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(550, easing = LinearEasing)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.padding(Spacing.lg),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                                ) {
                                    Box(
                                        modifier = Modifier.size(42.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Animated expanding ping halo
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .graphicsLayer {
                                                    scaleX = haloScale.value
                                                    scaleY = haloScale.value
                                                    alpha = haloAlpha.value
                                                }
                                                .background(StatusSuccessLight.copy(alpha = 0.45f), CircleShape)
                                        )
                                        // Bouncing spring checkmark
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = StatusSuccessLight,
                                            modifier = Modifier
                                                .size(34.dp)
                                                .graphicsLayer {
                                                    scaleX = checkmarkScale.value
                                                    scaleY = checkmarkScale.value
                                                }
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Your application is up to date",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Neo Child Clinic v${BuildConfig.VERSION_NAME} is the latest available release.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        UpdateCardUiState.Idle -> {
                            Spacer(Modifier.height(0.dp))
                        }
                    }
                }

                // 3. Update Preferences Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(Spacing.lg)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Automatic update checks",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Check once a day when online",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoChecksEnabled,
                                onCheckedChange = { viewModel.setAutoChecksEnabled(it) }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = Spacing.sm),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Update notes",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Show what changed after install",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = showNotesAfterInstall,
                                onCheckedChange = { showNotesAfterInstall = it }
                            )
                        }
                    }
                }

                // 4. Action & Security Notes Footer
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = formatLastChecked(lastCheckedTimestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = { viewModel.checkForUpdates(isManual = true) },
                        enabled = !checking && !installing,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (checking) {
                            val infiniteTransition = rememberInfiniteTransition(label = "btnSpin")
                            val rotation by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "btnRotation"
                            )
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer { rotationZ = rotation },
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text("Checking for updates…")
                        } else {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Spacing.sm))
                            Text("Check again")
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.padding(top = Spacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Verified release · Signed and verified before install.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(onClick = onBack) {
                        Text(
                            text = "Remind me later",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }
}
