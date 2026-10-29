package com.neochildclinic.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Neo Child Clinic — Warm Clinical System v2 Color Tokens.
 *
 * Visual philosophy: Warm Clinical / Gentle Precision.
 * Calming cream/sand foundation (#F7F4ED), Deep Maritime Teal (#16558F),
 * Gentle Charcoal ink (#14201F), and muted clinical status tokens.
 * All pairs verified for WCAG 2.2 AA / AAA contrast compliance.
 */

// Brand Primary (#16558F gives 7.69:1 on white, AAA compliant)
val ClinicBlue = Color(0xFF16558F)
val ClinicBlueDark = Color(0xFF9CC7FF)

// Clinical Sage Secondary
val ClinicSage = Color(0xFF4F6F5A)
val ClinicSageDark = Color(0xFFA8C8B4)

// Terracotta Accent / Tertiary
val ClinicAccent = Color(0xFFA9512A)
val ClinicAccentDark = Color(0xFFF0A47E)

// Warm Background & Surfaces
val BackgroundLight = Color(0xFFF7F4ED)
val BackgroundDark = Color(0xFF0E1512)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF151F1B)
val SurfaceAltLight = Color(0xFFF0ECE3)
val SurfaceAltDark = Color(0xFF1C2823)
val SurfaceSunkenLight = Color(0xFFEBE6DB)
val SurfaceSunkenDark = Color(0xFF22302A)

// Typography & Ink
val InkPrimaryLight = Color(0xFF14201F)
val InkPrimaryDark = Color(0xFFEEF3F0)
val InkSoftLight = Color(0xFF384A46)
val InkSoftDark = Color(0xFFC9D3CE)
val InkMutedLight = Color(0xFF5F6D68)
val InkMutedDark = Color(0xFF9AA8A1)

// Borders & Outlines
val OutlineLight = Color(0xFFD8D2C6)
val OutlineDark = Color(0xFF2C3A34)
val OutlineStrongLight = Color(0xFF7C8A84)
val OutlineStrongDark = Color(0xFF6E7F77)

// Status Tokens
val StatusDangerLight = Color(0xFFB42318)
val StatusDangerDark = Color(0xFFFFB4AB)
val StatusDangerContainerLight = Color(0xFFFDE4E1)
val StatusDangerContainerDark = Color(0xFF4E1511)
val StatusOnDangerContainerLight = Color(0xFF7A1A12)
val StatusOnDangerContainerDark = Color(0xFFFFDAD6)

val StatusWarningLight = Color(0xFF8A5300)
val StatusWarningDark = Color(0xFFF5C15C)
val StatusWarningContainerLight = Color(0xFFFFF0C9)
val StatusWarningContainerDark = Color(0xFF4A3406)
val StatusOnWarningContainerLight = Color(0xFF563400)
val StatusOnWarningContainerDark = Color(0xFFFFE2A3)

val StatusSuccessLight = Color(0xFF067647)
val StatusSuccessDark = Color(0xFF8DE0AB)
val StatusSuccessContainerLight = Color(0xFFDCF4E4)
val StatusSuccessContainerDark = Color(0xFF113C25)
val StatusOnSuccessContainerLight = Color(0xFF054F31)
val StatusOnSuccessContainerDark = Color(0xFFC6F5D7)

val StatusInfoLight = Color(0xFF175CD3)
val StatusInfoDark = Color(0xFFA8C8FF)
val StatusInfoContainerLight = Color(0xFFE4EDFD)
val StatusInfoContainerDark = Color(0xFF17325C)
val StatusOnInfoContainerLight = Color(0xFF0B357E)
val StatusOnInfoContainerDark = Color(0xFFD6E4FF)

// Chart series according to approved design tokens
val ChartPatients = Color(0xFF16558F)
val ChartConsultations = Color(0xFF4F6F5A)
val ChartVaccinations = Color(0xFFA9512A)
val ChartRevenue = Color(0xFF6941C6)
val ChartOnline = Color(0xFF067647)
val ChartCash = Color(0xFF0E7490)
val ChartNetProfit = Color(0xFF16558F)
val ChartCOGS = Color(0xFF8A5300)
val ChartExpenses = Color(0xFF0E7490)

// Dark-surface chart variants. The light chart tokens are too dark on the forest-ink
// background; these keep the same semantic series distinct at AA contrast.
val ChartPatientsDark = Color(0xFF9CC7FF)
val ChartConsultationsDark = Color(0xFFA8C8B4)
val ChartVaccinationsDark = Color(0xFFF0A47E)
val ChartRevenueDark = Color(0xFFD6BBFB)
val ChartOnlineDark = Color(0xFF8DE0AB)
val ChartCashDark = Color(0xFFF5C15C)
val ChartNetProfitDark = Color(0xFF9CC7FF)
val ChartCOGSDark = Color(0xFFF5C15C)
val ChartExpensesDark = Color(0xFF67E8F9)
