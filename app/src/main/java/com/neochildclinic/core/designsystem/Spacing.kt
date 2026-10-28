package com.neochildclinic.core.designsystem

import androidx.compose.ui.unit.dp

/**
 * Neo Child Clinic — Warm Clinical System v2 Spacing Scale.
 * 8-point / 4-point grid system.
 * Reference: design_tokens.json (screenPadding: 20, cardPadding: 16, sectionGap: 24, listGap: 12)
 */
object Spacing {
    /** 2dp / 4dp minimal gap */
    val xxs = 2.dp
    val xs = 4.dp

    /** 8dp - gap between list items, chip internal padding */
    val sm = 8.dp

    /** 12dp - dense card / list-row padding, gap inside a row */
    val md = 12.dp

    /** 16dp - field gap, standard card padding */
    val lg = 16.dp

    /** 20dp - screen horizontal gutter inset */
    val screen = 20.dp

    /** 24dp - gap between sections */
    val xl = 24.dp
    val section = 24.dp

    /** 32dp - major section header spacing */
    val xxl = 32.dp

    /** Card padding */
    val card = 16.dp
    val cardDense = 12.dp

    /** Minimum size for any interactive target (48dp touch target) */
    val touchTarget = 48.dp
}
