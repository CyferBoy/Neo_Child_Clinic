package com.neochildclinic.feature.settings.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.BuildConfig
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground

private const val SUPPORT_EMAIL = "support@neochildclinic.app"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSupportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val customColors = LocalCustomColors.current

    fun contactSupport() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$SUPPORT_EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, "Neo Child Clinic Support Request")
            putExtra(
                Intent.EXTRA_TEXT,
                "Please describe your question or issue:\n\n" +
                    "---\n" +
                    "App: Neo Child Clinic\n" +
                    "Version: ${BuildConfig.VERSION_NAME}\n"
            )
        }
        runCatching { context.startActivity(intent) }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Help & Support",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.screen),
                contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Top Banner Card (104dp height, matching help_support_light.svg)
                item {
                    HelpBannerCard(
                        email = SUPPORT_EMAIL,
                        onEmailClick = { contactSupport() }
                    )
                }

                // 2. Quick Help Section
                item {
                    Text(
                        text = "Quick help",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                // 4 Expandable Accordion Help Cards
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HelpAccordionCard(
                            title = "Add or edit a patient",
                            description = "Tap the '+' button on the Dashboard or go to Patients > Add Patient. Enter baby's details, date of birth, guardian name and phone number. To edit, open the patient card and tap the edit icon."
                        )
                        HelpAccordionCard(
                            title = "Record a vaccination and payment",
                            description = "Select a patient from the queue or search, tap 'Record Dose', select the administered vaccine and batch. Enter the fee collected, select payment mode (Cash or UPI), and confirm to deduct inventory."
                        )
                        HelpAccordionCard(
                            title = "Restore from a backup",
                            description = "Go to Settings > Backup & Restore > Restore. Select your encrypted backup (.nccb file) and enter your password. Choose 'Replace' to restore your full database or 'Merge' to combine records."
                        )
                        HelpAccordionCard(
                            title = "Manage staff roles and access",
                            description = "Administrators can view all staff, add new clinic members, set roles (Admin, Doctor, Nurse), assign permissions, and deactivate accounts from Settings > Manage staff."
                        )
                    }
                }

                // 3. Email Support Card (96dp height, soft warm container #F0ECE3)
                item {
                    EmailSupportCard(onSendEmail = { contactSupport() })
                }

                // 4. About & Legal Section
                item {
                    Text(
                        text = "About & legal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Privacy Policy
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Privacy Policy",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Last updated 15 Aug 2026",
                                        fontSize = 10.5.sp,
                                        color = customColors.textGrey
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = customColors.textGrey,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline,
                                thickness = 1.dp
                            )

                            // Terms of Service
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Terms of Service",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Last updated 15 Aug 2026",
                                        fontSize = 10.5.sp,
                                        color = customColors.textGrey
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = customColors.textGrey,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline,
                                thickness = 1.dp
                            )

                            // App Version
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "App version",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = BuildConfig.VERSION_NAME,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = customColors.textGrey
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 104dp Help Banner Card from help_support_light.svg:
 * Soft blue background `#DCE9F8`, circular white badge with question icon,
 * "How can we help?", "Browse answers or contact our support team.", "support@neochildclinic.app".
 */
@Composable
private fun HelpBannerCard(
    email: String,
    onEmailClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFDCE9F8),
        border = BorderStroke(1.dp, Color(0xFFC3D8EF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 44dp Circular white icon container
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = Color(0xFF16558F),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "How can we help?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0A2F52)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Browse answers or contact our support team.",
                    fontSize = 10.sp,
                    color = Color(0xFF0A2F52).copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = email,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0A2F52),
                    modifier = Modifier.clickable { onEmailClick() }
                )
            }
        }
    }
}

/**
 * 56dp (collapsed) Expandable Accordion Help Card matching SVG:
 * White card, 20dp corners, 1dp border, title, rotating chevron dropdown.
 */
@Composable
private fun HelpAccordionCard(
    title: String,
    description: String
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "chevron")
    val customColors = LocalCustomColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF384A46)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF5F6D68),
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = customColors.textGrey
                    )
                }
            }
        }
    }
}

/**
 * 96dp Email Support Card from help_support_light.svg:
 * Soft warm container `#F0ECE3`, Mail icon, "Email support", "We reply within one working day.", "Send email" action.
 */
@Composable
private fun EmailSupportCard(onSendEmail: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF0ECE3)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MailOutline,
                contentDescription = null,
                tint = Color(0xFF16558F),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Email support",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF14201F)
                )
                Text(
                    text = "We reply within one working day.",
                    fontSize = 9.5.sp,
                    color = Color(0xFF5F6D68)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Send email",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF16558F),
                    modifier = Modifier.clickable { onSendEmail() }
                )
            }
        }
    }
}
