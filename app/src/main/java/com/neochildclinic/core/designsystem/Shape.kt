package com.neochildclinic.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii. Replaces 72 inline RoundedCornerShape(n.dp) call sites.
 * `medium` (12dp) matches the radius StandardButton already used.
 *
 * Verified in design-system/neo-child-clinic/MASTER.md section 4.
 */
val NeoChildShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // badge, chip, table cell
    small = RoundedCornerShape(8.dp),       // list row, text field, icon button
    medium = RoundedCornerShape(12.dp),     // card, button
    large = RoundedCornerShape(16.dp),      // dialog, dropdown menu
    extraLarge = RoundedCornerShape(28.dp)  // bottom sheet, modal
)

/**
 * Stat tiles want a radius deliberately rounder than a card (20dp vs `medium`'s
 * 12dp), and it has no slot in [Shapes] - M3 fixes that scale at five entries.
 * Declared here so the value is named rather than a bare 20.dp at two call sites.
 */
val StatTileShape = RoundedCornerShape(20.dp)

/**
 * Outer wrapper for the dashboard chart canvases (BarLineChart, StatisticsTrendChart)
 * and the milestone summary card. Bigger than [StatTileShape] because it frames the
 * plot area, and [Shapes] tops out at 28dp - close enough that forcing these to
 * `extraLarge` would read as a redesign rather than a token migration.
 */
val ChartContainerShape = RoundedCornerShape(24.dp)
