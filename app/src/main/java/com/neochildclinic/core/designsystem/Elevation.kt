package com.neochildclinic.core.designsystem

import androidx.compose.ui.unit.dp

/**
 * Elevation levels. Shadow marks LAYERS - what floats above what - not decoration.
 * A dense list of shadowed cards reads as noise; separate rows with surface colour
 * instead and keep level0 as the default.
 *
 * Replaces inline `.dp` elevations on Card/Surface across screens.
 */
object Elevation {
    /** List rows, table cells. Separate with surface colour, not shadow. */
    val none = 0.dp

    /** Cards. */
    val card = 1.dp

    /** Dropdown menus, FAB. */
    val raised = 3.dp

    /** Dialogs. */
    val dialog = 6.dp

    /** Modal bottom sheets. */
    val sheet = 8.dp
}
