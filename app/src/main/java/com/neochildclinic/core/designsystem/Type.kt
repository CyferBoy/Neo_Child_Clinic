package com.neochildclinic.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * All 16 Material 3 roles. Previously only `bodyLarge` was declared and the other 15
 * fell through to the M3 defaults, on top of 27 hardcoded .sp values in screens.
 *
 * Two deliberate choices:
 *  - `letterSpacing = 0.sp` on bodyLarge. It was 0.5sp, which is display tracking;
 *    on 16sp running clinical text it reads loose and slows down list scanning.
 *  - `tnum` (tabular figures) on every style. Counts, ages, doses and rupee amounts
 *    line up in columns across StatisticsSummaryCard, FinanceTab and FullReportScreen.
 *
 * Font stays FontFamily.Default (Roboto). Bundling a face needs a font pipeline for
 * marginal gain - raise it separately if the clinic wants a brand face.
 */
private const val TABULAR_FIGURES = "tnum"

private val Trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = TABULAR_FIGURES,
    lineHeightStyle = Trim
)

val Typography = Typography(
    displayLarge = style(57, 64),
    displayMedium = style(45, 52),
    displaySmall = style(36, 44),

    headlineLarge = style(32, 40),
    headlineMedium = style(28, 36),
    headlineSmall = style(24, 32, FontWeight.SemiBold),

    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(16, 24, FontWeight.SemiBold, 0.15),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0.1),

    bodyLarge = style(16, 24),
    bodyMedium = style(14, 20, tracking = 0.25),
    bodySmall = style(12, 16, tracking = 0.4),

    labelLarge = style(14, 20, FontWeight.Medium, 0.1),
    labelMedium = style(12, 16, FontWeight.Medium, 0.5),
    labelSmall = style(11, 16, FontWeight.Medium, 0.5)
)
