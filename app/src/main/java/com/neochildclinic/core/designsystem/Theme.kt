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
 * Neo Child Clinic — Warm Clinical System v2 CustomColors.
 * Accent layer: semantic container/content pairs for badges, KPI tiles, and status indicators.
 * Verified with WCAG 2.2 AA / AAA contrast standards.
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
    val textPink: Color,
    val warningContainer: Color = Color(0xFFFFF0C9),
    val textWarning: Color = Color(0xFF563400),
    val successContainer: Color = Color(0xFFDCF4E4),
    val textSuccess: Color = Color(0xFF054F31),
    val surfaceAlt: Color = Color(0xFFF0ECE3),
    val surfaceSunken: Color = Color(0xFFEBE6DB),
    val outline: Color = Color(0xFFD8D2C6),
    val outlineStrong: Color = Color(0xFF7C8A84),
    val ink: Color = Color(0xFF14201F),
    val inkSoft: Color = Color(0xFF384A46),
    val muted: Color = Color(0xFF5F6D68)
)

val LightCustomColors = CustomColors(
    softBlue = Color(0xFFDCE9F8), // primaryContainer
    softGreen = Color(0xFFE0EBE2), // secondaryContainer
    softOrange = Color(0xFFF8E4D7), // accentContainer
    softPurple = Color(0xFFF0EAFB), // purpleContainer
    softCyan = Color(0xFFE4EDFD), // infoContainer
    softGrey = Color(0xFFEEF0F2), // neutralContainer
    softPink = Color(0xFFFDE4E1), // dangerContainer
    textBlue = Color(0xFF0A2F52), // onPrimaryContainer
    textGreen = Color(0xFF244032), // onSecondaryContainer
    textOrange = Color(0xFF5B2A12), // onAccentContainer
    textPurple = Color(0xFF3F2482), // onPurpleContainer
    textCyan = Color(0xFF0B357E), // onInfoContainer
    textGrey = Color(0xFF344054), // onNeutralContainer
    textPink = Color(0xFF7A1A12), // onDangerContainer
    warningContainer = Color(0xFFFFF0C9),
    textWarning = Color(0xFF563400),
    successContainer = Color(0xFFDCF4E4),
    textSuccess = Color(0xFF054F31),
    surfaceAlt = Color(0xFFF0ECE3),
    surfaceSunken = Color(0xFFEBE6DB),
    outline = Color(0xFFD8D2C6),
    outlineStrong = Color(0xFF7C8A84),
    ink = Color(0xFF14201F),
    inkSoft = Color(0xFF384A46),
    muted = Color(0xFF5F6D68)
)

val DarkCustomColors = CustomColors(
    softBlue = Color(0xFF123E6B),
    softGreen = Color(0xFF244A37),
    softOrange = Color(0xFF5C2C16),
    softPurple = Color(0xFF3B2A5C),
    softCyan = Color(0xFF17325C),
    softGrey = Color(0xFF2A3339),
    softPink = Color(0xFF4E1511),
    textBlue = Color(0xFFCFE3FF),
    textGreen = Color(0xFFC6E4D2),
    textOrange = Color(0xFFFFD9C6),
    textPurple = Color(0xFFEADDFF),
    textCyan = Color(0xFFD6E4FF),
    textGrey = Color(0xFFE3E8EC),
    textPink = Color(0xFFFFDAD6),
    warningContainer = Color(0xFF4A3406),
    textWarning = Color(0xFFFFE2A3),
    successContainer = Color(0xFF113C25),
    textSuccess = Color(0xFFC6F5D7),
    surfaceAlt = Color(0xFF1C2823),
    surfaceSunken = Color(0xFF22302A),
    outline = Color(0xFF2C3A34),
    outlineStrong = Color(0xFF6E7F77),
    ink = Color(0xFFEEF3F0),
    inkSoft = Color(0xFFC9D3CE),
    muted = Color(0xFF9AA8A1)
)

val LocalCustomColors = staticCompositionLocalOf { LightCustomColors }

// Full M3 ColorScheme implementing Warm Clinical System v2
private val LightColorScheme = lightColorScheme(
    primary = ClinicBlue, // #16558F
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE9F8),
    onPrimaryContainer = Color(0xFF0A2F52),
    secondary = ClinicSage, // #4F6F5A
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0EBE2),
    onSecondaryContainer = Color(0xFF244032),
    tertiary = ClinicAccent, // #A9512A
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF8E4D7),
    onTertiaryContainer = Color(0xFF5B2A12),
    error = StatusDangerLight, // #B42318
    onError = Color(0xFFFFFFFF),
    errorContainer = StatusDangerContainerLight, // #FDE4E1
    onErrorContainer = StatusOnDangerContainerLight, // #7A1A12
    background = BackgroundLight, // #F7F4ED Warm ivory foundation
    onBackground = InkPrimaryLight, // #14201F
    surface = SurfaceLight, // #FFFFFF
    onSurface = InkPrimaryLight, // #14201F
    surfaceVariant = SurfaceAltLight, // #F0ECE3
    onSurfaceVariant = InkSoftLight, // #384A46
    outline = OutlineLight, // #D8D2C6
    outlineVariant = SurfaceSunkenLight, // #EBE6DB
    scrim = Color(0xFF0B1512)
)

private val DarkColorScheme = darkColorScheme(
    primary = ClinicBlueDark, // #9CC7FF
    onPrimary = Color(0xFF07305B),
    primaryContainer = Color(0xFF123E6B),
    onPrimaryContainer = Color(0xFFCFE3FF),
    secondary = ClinicSageDark, // #A8C8B4
    onSecondary = Color(0xFF153527),
    secondaryContainer = Color(0xFF244A37),
    onSecondaryContainer = Color(0xFFC6E4D2),
    tertiary = ClinicAccentDark, // #F0A47E
    onTertiary = Color(0xFF4A1E0B),
    tertiaryContainer = Color(0xFF5C2C16),
    onTertiaryContainer = Color(0xFFFFD9C6),
    error = StatusDangerDark, // #FFB4AB
    onError = Color(0xFF690005),
    errorContainer = StatusDangerContainerDark, // #4E1511
    onErrorContainer = StatusOnDangerContainerDark, // #FFDAD6
    background = BackgroundDark, // #0E1512 Forest ink
    onBackground = InkPrimaryDark, // #EEF3F0
    surface = SurfaceDark, // #151F1B
    onSurface = InkPrimaryDark, // #EEF3F0
    surfaceVariant = SurfaceAltDark, // #1C2823
    onSurfaceVariant = InkSoftDark, // #C9D3CE
    outline = OutlineDark, // #2C3A34
    outlineVariant = SurfaceSunkenDark, // #22302A
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
