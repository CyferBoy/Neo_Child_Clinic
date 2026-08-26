package com.neochildclinic.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.core.ui.FoldedCornerAccent
import com.neochildclinic.core.ui.FoldedCornerShape

@Composable
fun DashboardCardSmall(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    badge: String? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(90.dp)
            .shadow(
                elevation = 1.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = Color.Black.copy(alpha = 0.05f)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(containerColor)
            .clickable { onClick() }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
fun DashboardCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    badge: String? = null,
    height: Dp = 180.dp,
    isRestricted: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(height)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color.Black.copy(alpha = 0.05f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(if (isRestricted) containerColor.copy(alpha = 0.4f) else containerColor)
            .clickable(enabled = !isRestricted) { onClick() }
            .padding(16.dp)
    ) {
        // Optional top badge
        if (badge != null && !isRestricted) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(
                        color = contentColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }

        // Card Content
        Column(
            modifier = Modifier.fillMaxSize().alpha(if (isRestricted) 0.5f else 1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor.copy(alpha = 0.7f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
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
