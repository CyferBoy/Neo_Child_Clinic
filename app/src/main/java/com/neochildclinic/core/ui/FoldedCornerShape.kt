package com.neochildclinic.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A rounded-rectangle [Shape] with a rounded notch cut out of the bottom-end corner,
 * giving a "folded page" / dog-ear silhouette like a document icon. Draw a small
 * rounded square (see usage in PatientListScreen's PatientCard) positioned in the
 * bottom-end corner to fill the notch with an accent color.
 */
class FoldedCornerShape(
    private val cornerRadius: Dp = 20.dp,
    private val notchSize: Dp = 44.dp,
    private val notchCornerRadius: Dp = 14.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cr = with(density) { cornerRadius.toPx() }
        val notch = with(density) { notchSize.toPx() }
        val ncr = with(density) { notchCornerRadius.toPx() }
        val w = size.width
        val h = size.height

        val path = Path().apply {
            // Top-left corner, going clockwise
            moveTo(cr, 0f)
            lineTo(w - cr, 0f)
            arcTo(Rect(w - 2 * cr, 0f, w, 2 * cr), -90f, 90f, false)

            // Right edge down to the top of the notch
            lineTo(w, h - notch - ncr)
            arcTo(Rect(w - 2 * ncr, h - notch - ncr, w, h - notch + ncr), 0f, 90f, false)

            // Into the notch (top edge of the cut-out, going left)
            lineTo(w - notch + ncr, h - notch)
            arcTo(Rect(w - notch - ncr, h - notch, w - notch + ncr, h - notch + 2 * ncr), -90f, -90f, false)

            // Down the inner edge of the notch to the bottom
            lineTo(w - notch, h - cr)
            arcTo(Rect(w - notch - 2 * cr, h - 2 * cr, w - notch, h), 0f, 90f, false)

            // Bottom edge to bottom-left corner
            lineTo(cr, h)
            arcTo(Rect(0f, h - 2 * cr, 2 * cr, h), 90f, 90f, false)

            // Left edge up to top-left corner
            lineTo(0f, cr)
            arcTo(Rect(0f, 0f, 2 * cr, 2 * cr), 180f, 90f, false)

            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Plain accent square meant to sit in the notch cut from [FoldedCornerShape] on a card,
 * recreating the "folded page" tab look. Purely decorative (no click behavior).
 */
@Composable
fun FoldedCornerAccent(
    accentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    cornerRadius: Dp = 11.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(accentColor)
    )
}

/**
 * Tappable accent square for the notch cut from [FoldedCornerShape], showing a small icon
 * (defaults to a "+"). Use this on cards whose folded corner should double as a quick action,
 * e.g. "add a new patient" tucked into the corner of the Patient List card.
 */
@Composable
fun FoldedCornerAccentButton(
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.White,
    icon: ImageVector = Icons.Default.Add,
    contentDescription: String? = "Add",
    size: Dp = 30.dp,
    cornerRadius: Dp = 11.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(accentColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}
