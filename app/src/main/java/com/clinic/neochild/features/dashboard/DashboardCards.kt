package com.clinic.neochild.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DashboardCardSmall(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color = MaterialTheme.colorScheme.onErrorContainer,
    badge: String? = null,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (badge != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    color = contentColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = contentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
fun DashboardCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color = MaterialTheme.colorScheme.onErrorContainer,
    badge: String? = null,
    height: Dp = 160.dp,
    isRestricted: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        onClick = { if (!isRestricted) onClick() },
        modifier = modifier.height(height).fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRestricted) containerColor.copy(alpha = 0.4f) else containerColor
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isRestricted) 0.dp else 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (badge != null && !isRestricted) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    color = contentColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .alpha(if (isRestricted) 0.5f else 1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = contentColor
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    fontSize = 15.sp
                )
            }

            if (isRestricted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Don't have access",
                        modifier = Modifier.rotate(-20f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.Red.copy(alpha = 0.4f),
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
