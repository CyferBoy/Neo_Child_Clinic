package com.neochildclinic.core.designsystem

import androidx.compose.ui.unit.dp

/**
 * 4dp-based spacing scale. Replaces the 947 inline `.dp` literals.
 *
 * Plain object rather than a CompositionLocal: spacing is theme-independent, so
 * there is nothing to provide at the theme boundary and no @Composable needed
 * at the call site.
 *
 * Rule of thumb: 8/12dp inside components and list rows, 16dp at the screen
 * gutter, 24dp between sections. Anything else is probably a one-off - measure
 * it before adding to this scale.
 */
object Spacing {
    /** 4dp - gap between an icon and its label inside a chip. */
    val xs = 4.dp

    /** 8dp - gap between list items, chip internal padding. */
    val sm = 8.dp

    /** 12dp - dense card / list-row padding, gap inside a row. */
    val md = 12.dp

    /** 16dp - screen gutter, field gap. The default. */
    val lg = 16.dp

    /** 24dp - gap between sections. */
    val xl = 24.dp

    /** 32dp - gap above a major section. */
    val xxl = 32.dp

    /** Horizontal screen padding. */
    val screen = 16.dp

    /** Card padding. Use `cardDense` for list and statistics contexts. */
    val card = 16.dp
    val cardDense = 12.dp

    /** Minimum size for any interactive target (Android/Material, not iOS's 44pt). */
    val touchTarget = 48.dp
}
