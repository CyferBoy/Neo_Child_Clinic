package com.neochildclinic.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Accent layer: 7 semantic container/content pairs for badges, KPI tiles and chart
 * legend swatches. Every pair is verified in design-system/neo-child-clinic/contrast_check.py
 * (5.10:1-9.72:1 light, 5.27:1-10.81:1 dark).
 *
 * This layer is NOT a second background palette. Surfaces, on-surface text and
 * outlines come from MaterialTheme.colorScheme only - see MASTER.md section 1.1.
 */
@Immutable
data class CustomColors(
    val softBlue: Color,
    val softGreen: Color,
    val softOrange: Color,
    val softPurple: Color,
    val softCyan: Color,
    val softGrey: Color,
    val softPink: Color,
    val textBlue: Color,
    val textGreen: Color,
    val textOrange: Color,
    val textPurple: Color,
val textCyan: Color,
       val textGrey: Color,
       val textPink: Color
   )

val LightCustomColors = CustomColors(
    softBlue = Color(0xFFD6E4F0),
    softGreen = Color(0xFFDCF0E2),
    softOrange = Color(0xFFFFE8D1),
    softPurple = Color(0xFFF2E4F6),
    softCyan = Color(0xFFD9F2F0),
    softGrey = Color(0xFFEBEBEB),
    softPink = Color(0xFFFCE4E4),
    textBlue = Color(0xFF1E3A5F),
    textGreen = Color(0xFF1B5E20),
    textOrange = Color(0xFF9C4D04),
    textPurple = Color(0xFF4A148C),
    textCyan = Color(0xFF00695C),
    textGrey = Color(0xFF424242),
    textPink = Color(0xFFB71C1C),
    )

val DarkCustomColors = CustomColors(
    softBlue = Color(0xFF004977),
    softGreen = Color(0xFF005231),
    softOrange = Color(0xFF723600),
    softPurple = Color(0xFF553F5F),
    softCyan = Color(0xFF004D40),
    softGrey = Color(0xFF2C2C2C),
    softPink = Color(0xFF632E2E),
    textBlue = Color(0xFFC2E8FF),
    textGreen = Color(0xFF8FF7BF),
    textOrange = Color(0xFFFFDDB1),
    textPurple = Color(0xFFF2DAFF),
    textCyan = Color(0xFF80CBC4),
    textGrey = Color(0xFFE2E2E6),
    textPink = Color(0xFFFFDAD6),
    )

val LocalCustomColors = staticCompositionLocalOf { LightCustomColors }

// Full M3 role coverage. Previously only 4 light / 9 dark roles were declared, so the
// rest fell through to Material 3's baseline purple - which is why the app drawer
// (AppDrawer.kt uses primaryContainer) rendered purple against an otherwise blue app.
private val LightColorScheme = lightColorScheme(
    primary = ClinicBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4F0),
    onPrimaryContainer = Color(0xFF1E3A5F),
    secondary = Color(0xFF00695C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9F2F0),
    onSecondaryContainer = Color(0xFF00695C),
    tertiary = Color(0xFF6A1B9A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF2E4F6),
    onTertiaryContainer = Color(0xFF4A148C),
    // Was never declared in either scheme, yet used by ~10 screens. colorScheme.error
    // was silently resolving to Material 3's baseline red.
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCE4E4),
    onErrorContainer = Color(0xFFB71C1C),
    background = Color(0xFFFBF8F5),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFBF8F5),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFEBEBEB),
    onSurfaceVariant = Color(0xFF424242),
    outline = Color(0xFF8A8A8A),
    outlineVariant = Color(0xFFDEDEDE),
    scrim = Color(0xFF000000)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF92CCFF),
    onPrimary = Color(0xFF003355),
    primaryContainer = Color(0xFF004977),
    onPrimaryContainer = Color(0xFFC2E8FF),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF00352F),
    secondaryContainer = Color(0xFF004D40),
    onSecondaryContainer = Color(0xFF80CBC4),
    tertiary = Color(0xFFE0BBE4),
    onTertiary = Color(0xFF381E40),
    tertiaryContainer = Color(0xFF553F5F),
    onTertiaryContainer = Color(0xFFF2DAFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFC4C6C7),
    outline = Color(0xFF8E8E8E),
    outlineVariant = Color(0xFF3A3A3A),
    scrim = Color(0xFF000000)
)

@Composable
fun NeoChildTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalCustomColors provides if (darkTheme) DarkCustomColors else LightCustomColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            shapes = NeoChildShapes,
            content = content
        )
    }
}
