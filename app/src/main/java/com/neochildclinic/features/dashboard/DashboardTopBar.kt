package com.neochildclinic.features.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.R
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.repository.SyncState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTopBar(
    onMenuClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                "Dashboard",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = Color(0xFF2C2C2C)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = Color(0xFF2C2C2C),
            navigationIconContentColor = Color(0xFF2C2C2C)
        )
    )
}

@Composable
fun ClinicLogo(isWideScreen: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "Clinic Logo",
            modifier = Modifier.size(if (isWideScreen) 180.dp else 140.dp)
        )
    }
}
