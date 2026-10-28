package com.neochildclinic.core.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Neo Child Clinic — Warm Clinical System v2 Corner Radii (Gentle Precision).
 *
 * xs (6dp): badges, small tags, mini chips
 * sm (10dp): search bars, dense rows, chips
 * input (12dp): text fields, dropdowns
 * button (14dp): standard buttons, action triggers
 * card (20dp): cards, timeline cards, stat tiles
 * dialog (24dp): alert and confirmation dialogs
 * hero/sheet (28dp): modal bottom sheets, hero containers
 * pill (999dp): status chips, filter pills
 */
val NeoChildShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),   // badge, chip, small tag
    small = RoundedCornerShape(10.dp),      // list row, search bar
    medium = RoundedCornerShape(14.dp),     // button, action trigger
    large = RoundedCornerShape(20.dp),      // card, container
    extraLarge = RoundedCornerShape(28.dp)  // bottom sheet, hero
)

val CardShape = RoundedCornerShape(20.dp)
val StatTileShape = RoundedCornerShape(20.dp)
val ChartContainerShape = RoundedCornerShape(24.dp)
val DialogShape = RoundedCornerShape(24.dp)
val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
val ButtonShape = RoundedCornerShape(14.dp)
val FieldShape = RoundedCornerShape(12.dp)
val PillShape = CircleShape
