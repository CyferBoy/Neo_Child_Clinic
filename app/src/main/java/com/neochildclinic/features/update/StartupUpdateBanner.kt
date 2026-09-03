package com.neochildclinic.features.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** How long the startup popup stays visible before auto-dismissing itself. */
private const val STARTUP_POPUP_VISIBLE_MILLIS = 4_500L

/**
 * Small, temporary heads-up popup shown at the top of the screen on app launch when a
 * newer version is available. This is NOT the full "Update Available" dialog and NOT an
 * Android notification - it's transient in-app UI only:
 *  - Auto-dismisses itself a few seconds after appearing, with no action required.
 *  - Tapping it opens the existing [AppUpdateDialog] via [onTap] rather than duplicating
 *    any of that dialog's content (What's New / Download & Install / Don't remind / Later)
 *    here.
 *
 * The caller is responsible for only showing this for a genuine UpdateType.UPDATE result
 * (see AppUpdateViewModel.checkForUpdates) - this composable itself has no opinion on when
 * it should appear, only on how it behaves once shown.
 */
@Composable
fun StartupUpdateBanner(
    info: AppUpdateInfo,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Keyed on the version so a (rare) different popup value while one is already showing
    // restarts the auto-dismiss window rather than firing early against the old timer.
    LaunchedEffect(info.versionCode) {
        delay(STARTUP_POPUP_VISIBLE_MILLIS)
        onDismiss()
    }

    Surface(
        onClick = onTap,
        modifier = modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Update available",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Vaccine Manager v${info.versionName} is ready. Tap to view.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
