package com.neochildclinic.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Brand + chart tokens. Everything else lives in [Theme.kt] as MaterialTheme roles
 * or in the [CustomColors] accent layer.
 *
 * Every value here is verified by design-system/neo-child-clinic/contrast_check.py.
 * Don't add a color without running it.
 */

// Brand. Was #007BFF - failed WCAG AA at 3.98:1 (white button label) and 3.88:1 (as text).
// #0059B8 gives 6.73:1 and 6.56:1. One change repaired every primary button and field label.
val ClinicBlue = Color(0xFF0059B8)

// Chart series. Non-text, 3:1 floor against the light surface.
// Revenue and Cash were #FF9800 (2.10:1) and #4CAF50 (2.71:1) - both below it.
val ChartPatients = Color(0xFF2196F3)
val ChartConsultations = Color(0xFF9C27B0)
val ChartVaccinations = Color(0xFF009688)
val ChartRevenue = Color(0xFFB45309)
val ChartOnline = Color(0xFF3F51B5)
val ChartCash = Color(0xFF1B5E20)
val ChartNetProfit = Color(0xFFE91E63)
val ChartCOGS = Color(0xFFFF5722)
val ChartExpenses = Color(0xFF795548)
